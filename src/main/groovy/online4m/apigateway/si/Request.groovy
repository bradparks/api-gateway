package online4m.apigateway.si

import java.util.UUID

import groovy.transform.ToString
import groovy.transform.TupleConstructor

enum RequestMethod {
  POST, GET, PUT, DELETE
}

enum RequestMode {
  SYNC, ASYNC, EVENT
}

enum RequestFormat {
  // Content-Type: application/json
  JSON,
  // Content-Type: application/xml
  XML, 
  // Content-Type: application/x-www-form-urlencoded
  URLENC
}

@ToString @TupleConstructor
class Request {
  UUID          uuid
  RequestMethod method
  RequestMode   mode
  RequestFormat format
  URL           url
  // HTTP request headers in form of key-value pairs. For example:
  //    "Authorization": "Bearer ACCESS_KEY"
  Map           headers
  // HTTP request data to be sent as either query attributes or body content
  Object        data

  static Request build(Map data) {
    Request req = new Request()
    req.uuid = data.uuid ? UUID.fromString(data.uuid) : UUID.randomUUID()
    req.method = data.method as RequestMethod
    req.mode = data.mode as RequestMode
    req.format = data.format as RequestFormat
    req.url = data.url.toURL()
    req.headers = data.headers ?: [:]
    req.data = data.data ?: []
    return req
  }
}
