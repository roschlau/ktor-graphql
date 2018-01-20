package com.github.roschlau.ktor.graphql

import com.coxautodev.graphql.tools.GraphQLResolver
import com.coxautodev.graphql.tools.SchemaParser
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.pipeline.PipelineContext
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post

const val DEFAULT_GQL_ROUTE = "/graphql"
const val DEFAULT_SCHEMA_FILE = "schema.graphqls"

fun Route.graphQL(
    schemaFile: String,
    vararg resolvers: GraphQLResolver<*>
) {
    graphQL(DEFAULT_GQL_ROUTE, GraphQLEndpoint(buildSchema(schemaFile, *resolvers), { it }))
}

fun <Context> Route.graphQL(
    route: String = DEFAULT_GQL_ROUTE,
    schemaFile: String = DEFAULT_SCHEMA_FILE,
    provideContext: (ApplicationCall) -> Context,
    vararg resolvers: GraphQLResolver<*>
) {
    graphQL(route, GraphQLEndpoint(buildSchema(schemaFile, *resolvers), provideContext))
}

private fun buildSchema(schemaFile: String, vararg resolvers: GraphQLResolver<*>) =
    SchemaParser.newParser()
        .file(schemaFile)
        .resolvers(*resolvers)
        .build()
        .makeExecutableSchema()

/**
 * Sets up an endpoint that responds to GraphQL queries using the passed instance of [GraphQLEndpoint] at the given
 * [route]. The endpoint responds to GET requests as well as POST requests following the convention specified in
 * [the GraphQL documentation](http://graphql.org/learn/serving-over-http/#http-methods-headers-and-body)
 */
fun <Context> Route.graphQL(
    route: String = DEFAULT_GQL_ROUTE,
    endpoint: GraphQLEndpoint<Context>
) {
    get(route) { doGraphQLCall(endpoint) }
    post(route) { doGraphQLCall(endpoint) }
}

suspend fun <Context> PipelineContext<Unit, ApplicationCall>.doGraphQLCall(endpoint: GraphQLEndpoint<Context>) {
    val result = try {
        endpoint.processCall(call)
    } catch (e: InvalidRequestFormatException) {
        return call.respond(HttpStatusCode.BadRequest, "The GraphQL query could not be parsed")
    }
    return call.respondText(result, ContentType.Application.Json)
}