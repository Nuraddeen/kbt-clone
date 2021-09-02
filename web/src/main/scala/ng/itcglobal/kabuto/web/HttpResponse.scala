package ng.itcglobal.kabuto
package web

import spray.json.JsValue

import  ng.itcglobal.kabuto._
import core.util.Enum.HttpResponseStatus
 
object HttpResponse {
    
case class BetasoftApiHttpResponse(
  status: HttpResponseStatus.Value,
  description: String,
  code: Option[Int]  = None,
  data: Option[JsValue] = None
  )

  
}
