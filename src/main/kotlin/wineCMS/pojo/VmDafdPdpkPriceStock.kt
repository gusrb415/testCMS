package wineCMS.pojo

import java.math.BigDecimal

class VmDafdPdpkPriceStock (val wineRefEXT: String? = null, var vintageTag: Short? = null, var vintageStr: String? = null,
                            val pckgType: String? = null, var pckgTypeStr: String? = null,
                            val pckgRefEXT: String? = null, val pckgName : String? = null, val pckgNameCht: String? = null,
                            val pckgNameChs: String? = null, val hrDlvMax: Long? = null, val hrDlvMin: Long? = null,
                            var qty: Long? = null, val price: BigDecimal? = null,  val pckgImgUrl: String? = null)