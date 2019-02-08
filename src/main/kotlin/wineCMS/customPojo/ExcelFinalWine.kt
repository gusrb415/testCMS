package wineCMS.customPojo

import wineCMS.pojo.VmDafdPdpkVinImport

class ExcelFinalWine(val vinImport: VmDafdPdpkVinImport, val index: Int, val aOrB: String, val success: Boolean, val reason: String?,
                     val disappear: Boolean, var newWine: Boolean, var newVint: Boolean, var newPack: Boolean)