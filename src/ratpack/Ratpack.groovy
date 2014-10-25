import static ratpack.groovy.Groovy.groovyTemplate
import static ratpack.groovy.Groovy.ratpack

import ratpack.server.PublicAddress

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import groovy.xml.MarkupBuilder

import groovy.json.JsonSlurper

import groovy.transform.TupleConstructor

import ratpack.registry.Registries
import ratpack.rx.RxRatpack
import ratpack.codahale.metrics.CodaHaleMetricsModule
import ratpack.perf.incl.*
import ratpack.codahale.metrics.HealthCheckHandler
import com.codahale.metrics.health.HealthCheckRegistry

import online4m.apigateway.health.CallerServiceHealthCheck

import online4m.apigateway.si.CallerModule
import online4m.apigateway.si.CallerService
import online4m.apigateway.si.CallerServiceAsync
import online4m.apigateway.si.QueryServiceAsync
import online4m.apigateway.si.Request
import online4m.apigateway.si.Response
import online4m.apigateway.si.Utils
import online4m.apigateway.ds.CallerDSModule
import online4m.apigateway.si.CallerServiceCtx


final Logger log = LoggerFactory.getLogger(Ratpack.class)

ratpack {
  bindings {
    add new CodaHaleMetricsModule().metrics().jvmMetrics().healthChecks().jmx()
    bind CallerServiceHealthCheck
    add new CallerModule()
    add new CallerDSModule()

    init {
      RxRatpack.initialize()
    }
  }

  handlers {
    get {
      render groovyTemplate("index.html", title: "My Ratpack App")
    }

    prefix("api") {
      handler { CallerServiceCtx csCtx, PublicAddress publicAddress ->
        // common functionality for all the other REST methods
        if (csCtx && !csCtx.serverUrl) {
          csCtx.serverUrl = publicAddress.getAddress(context).toString()
          log.debug("COMMON HANDLER: ${csCtx?.toString()}")
        }

        // log HTTP header names and their values
        /* request.headers?.names?.each {name -> */
        /*   log.debug("HEADER ${name}, VALUES: ${request.headers?.getAll(name)}") */
        /* } */
        // call next() to process request
        next()
      }

      // get list of available APIs - follow HAL hypertext application language conventions
      get { CallerServiceCtx csCtx ->
        String serverUrl = csCtx.serverUrl
        // IMPORTANT: element of structure cannot be keyword like "call" because then Groovy tries to call it.
        def links = [
          _links: [
            self: [
              href: serverUrl + "/api"
            ],
            "call-api": [
              href: serverUrl + "/api/call",
              title: "Call external API"
            ],
            "health-checks": [
              href: serverUrl + "/api/health-checks",
              title: "Run all health checks"
            ],
            "health-check-named": [
              href: serverUrl + "/api/call/health-check/:name",
              templated: true,
              title: "Available: apigateway"
            ]
          ]
        ]

        byContent {
          json {
            render JsonOutput.prettyPrint(JsonOutput.toJson(links))
          }
          type("application/hal+json") {
            render JsonOutput.prettyPrint(JsonOutput.toJson(links))
          }
          xml {
            render Utils.buildXmlString(links)
          }
        }
      }

      // get named health check
      get("health-check/:name", new HealthCheckHandler())

      // run all health checks
      get("health-checks") { HealthCheckRegistry healthCheckRegistry ->
        render healthCheckRegistry.runHealthChecks().toString()
      }

      // call reactive way - RxJava
      post("call") { CallerServiceAsync callerServiceAsync ->
        callerServiceAsync.invokeRx(request.body.text).single().subscribe() { Response response ->
          log.debug "BEFORE JsonOutput.toJson(response)"
          //getResponse().status(201)
          render JsonOutput.toJson(response)
        }
      }
      
      // call with ratpack promise
      post("call1") { CallerServiceAsync callerService ->
        callerService.invoke(request.body.text).then {
          render JsonOutput.toJson(it)
        }
      }

      // call with ratpack blocking code (it is running in seperate thread)
      post("call2") {CallerService callerService ->
        blocking {
          return callerService.invoke(request.body.text)
        }.then {
          render JsonOutput.toJson(it) // response.toString()
        }
      }

      post("call3") { CallerService callerService ->
        Response response = callerService.invoke(request.body.text)
        render JsonOutput.toJson(response)
      }

      get("call/response/:uuid") { QueryServiceAsync queryService ->
        def suuid = pathTokens["uuid"]
        queryService.getResponseRx(suuid).single().subscribe() { Response response ->
          render JsonOutput.toJson(response)
        }
      }

      get("bycontent") {
        byContent {
          json {
            // if HTTP header: Accept is not given then first type is returned as default
            // so json is default return format
            log.debug("RESPOND JSON")
            def builder = new JsonBuilder()
            builder.root {
              type "JSON"
            }
            render builder.toString()
          }
          xml {
            log.debug("RESPOND XML")
            def swriter = new StringWriter()
            new MarkupBuilder(swriter).root {
              type(a: "A", b: "B", "XML")
            }
            render swriter.toString()
          }
        }
      }
    }

    assets "public"
  }
}
