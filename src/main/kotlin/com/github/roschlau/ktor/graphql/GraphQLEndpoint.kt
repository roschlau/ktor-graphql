package com.github.roschlau.ktor.graphql

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.ktor.application.ApplicationCall
import io.ktor.request.contentType
import io.ktor.request.receiveText

private val gson = GsonBuilder().create()

class GraphQLEndpoint<Context>(
    private val schema: GraphQLSchema,
    private val provideContext: (ApplicationCall) -> Context
) {

    /**
     * Attempts to extract a GraphQL query from the passed [call] object, executes it, an returns the result as JSON.
     * Getting the query, operation name and variables from the [call] follows the best practices laid out in the
     * [GraphQL specification](http://graphql.org/learn/serving-over-http/), as does the format of the returned String.
     */
    @Throws(InvalidRequestFormatException::class)
    suspend fun processCall(call: ApplicationCall): String {
        val request = try {
            GraphQLRequest.from(call)
        } catch (e: JsonSyntaxException) {
            throw InvalidRequestFormatException("GraphQL request could not be parsed.", e)
        }
        return newGraphQL()
            .execute(request.toExecutionInput(provideContext(call)))
            .toSpecificationJson()
    }

    private fun GraphQLRequest.toExecutionInput(context: Context): ExecutionInput {
        return ExecutionInput(query, operationName, context, "Root", variables)
    }

    private fun ExecutionResult.toSpecificationJson() = gson.toJson(this.toSpecification())

    private fun newGraphQL() = GraphQL.newGraphQL(schema).build()

}

class InvalidRequestFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

private data class GraphQLRequest(
    val query: String = "",
    val operationName: String? = null,
    val variables: Map<String, Any>? = null
) {

    companion object {

        /**
         * Extracts a GraphQLRequest from the [call] according to the
         * [GraphQL Specification](http://graphql.org/learn/serving-over-http/#http-methods-headers-and-body).
         */
        @Throws(JsonSyntaxException::class)
        suspend fun from(call: ApplicationCall): GraphQLRequest {
            val contentType = call.request.contentType().contentSubtype
            return when {
                call.parameters["query"] != null -> requestFromParameters(call)
                contentType == "graphql" -> GraphQLRequest(call.receiveText())
                else -> gson.fromJson(call.receiveText())
            }
        }

        /**
         * Builds a [GraphQLRequest] instance from the `query`, `operationName` and `variables` parameters of the [call]
         * object. These can either be GET- or POST-parameters.
         */
        @Throws(JsonSyntaxException::class)
        private fun requestFromParameters(call: ApplicationCall): GraphQLRequest {
            val query = call.parameters["query"]!!
            val operationName = call.parameters["operationName"]
            val variables = call.parameters["variables"]?.let { paramValue ->
                gson.fromJson<Map<String, Any>>(paramValue)
            }
            return GraphQLRequest(query, operationName, variables)
        }
    }
}

@Throws(JsonSyntaxException::class)
private inline fun <reified T> Gson.fromJson(json: String): T {
    val typeToken = object : TypeToken<T>() {}
    return fromJson<T>(json, typeToken.type)
}