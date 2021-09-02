package ng.itcglobal.kabuto
package core.util


object Enum {

  object ImageTypes extends Enumeration {
    val Jpeg = Value("jpeg")
    val Jpg  = Value("jpg")
    val Tiff = Value("tiff")
    val Png  = Value("png")
    val Bmp  = Value("bmp")
    val Gif  = Value("gif")
    val Webp = Value("webp")
    val Pdf  =  Value("pdf")

  }


  object HttpResponseStatus extends Enumeration {
    val Queued              = Value(101)
    val PendingConfirmation = Value(102)
    val PendingValidation   = Value(103)
    val Validated           = Value(104)
    val Booked              = Value(105)
    val NotFound            = Value(106)
    val InValidAmount       = Value(107)
    val NotSupported        = Value(201)
    val InsufficientFunds   = Value(202)
    val ApplicationError    = Value(203)
    val NotAllowed          = Value(204)
    val DuplicateRequest    = Value(205)
    val Success             = Value(200)
    val Failed              = Value(400)
    val Throttled           = Value(401)
    val Expired             = Value(402)
    val Reversed            = Value(500)

  }


}
