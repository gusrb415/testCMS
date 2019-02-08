package wineCMS.pojo

class VmDafdPdpkVinImport(val pdpk: VmDafdPdpkPriceStock, val vin: VmDafdVintageMeta? = null,
                          var wineSeo: String? = null, var productId: Long? = null,
                          var prodpckgId: Long? = null, var errMsg: String? = null)