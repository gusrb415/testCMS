package wineCMS.wineAPI

import com.beust.klaxon.*
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import wineCMS.customPojo.PackageHolder
import wineCMS.customPojo.ProductHolder
import wineCMS.pojo.*
import java.math.BigDecimal
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import log
import java.io.ByteArrayInputStream

@Suppress("UNCHECKED_CAST")
class APICallFunc {
    //////API URLs//////
    private val baseDomain = "https://demo01-dbtest.myicellar.com:8998"

    private val updateUrl = "$baseDomain/cmsapi/datafeed-product/debug-test/update"
    private val approveUrl = "$baseDomain/cmsapi/datafeed-product/debug-test/approve"
    private val getWineDataUrl = "$baseDomain/cmsapi2/wine-meta/wine/list"
    private val getWineDataByProductIdUrl = "$baseDomain/cmsapi2/wine-meta/wine/detail"
    private val getProductsUrl = "$baseDomain/cmsapi/ecom/product/meta/detail"
    private val getWineSeoListUrl = "$baseDomain/cmsapi2/common/debug-test/wine-seo-list"
    private val getWineryNoteUrl = "$baseDomain/cmsapi2/wine-meta/winery/detail"

    //////STATIC OBJECT//////
    companion object {
        val moshi = Moshi.Builder()
                .add(BigDecimalAdapter())
                .build()!!

        //////MINI DATABASES//////
        private val miniWineDB: MutableMap<String, Map<Short, VmDafdVintageMeta>> = mutableMapOf()
        private val miniProductDB: MutableMap<Long, Pair<ProductHolder, Map<Long, PackageHolder>>> = mutableMapOf()
    }

    //////CALL WRAPPER//////
    /**
     * If json string is needed as a response, this method is called instead which returns the response in json string
     * @param jsonObjectString receive any json object in json string format
     * @param url call POST request to this url
     * @param appToken Token to authorize this POST request
     * @return any response in json string
     */
    private fun httpCallRStr(jsonObjectString: String, url: String, appToken: String, isArray: Boolean = false): String {
        val any = httpCall(jsonObjectString, url, appToken)

        return try {
            if (isArray) {
                (any as JsonArray<JsonObject>).toJsonString(true)
            } else {
                (any as JsonObject).toJsonString(true)
            }
        } catch (e: Exception) {
            throw IllegalStateException("API Failed to convert into json")
        }
    }

    /**
     * It receives parameters and call POST call on the url then return the response if successful
     * @param jsonObjectString receive any json object in json string format
     * @param url call POST request to this url
     * @param appToken Token to authorize this POST request
     * @return any response in json object or array
     */
    private fun httpCall(jsonObjectString: String, url: String, appToken: String): Any? {
        val authToken = "78c97d2c8c3db3a965454492de571716ef13f0504cacfa7e4244174ab2ae2d0c382fcdc61456722522b0a23bf7570fc88eb53a30fd738627ca8746b475d52cce"

        val headerList = mapOf(
                "auth-token" to authToken,
                "Content-Type" to "application/json"
        )

        val body =
"""{
    "header":{"appToken":"$appToken"},
    "content":
$jsonObjectString
}"""
        val (request, response, result) = url.httpPost().body(body).header(headerList).responseString()

        return when (result) {
            is Result.Failure -> {
                throw IllegalStateException("API Response error : ${response.statusCode}\n$request\n$url")
            }
            is Result.Success -> {
                val json = Parser().parse(ByteArrayInputStream(result.component1()!!.toByteArray()), Charsets.UTF_8) as JsonObject
                val jsonHeader = json["header"] as JsonObject
                if (jsonHeader["isSuccess"] == false) {
                    log.logAppend("Error Code: ${jsonHeader["errorCode"]}, Error Message: ${jsonHeader["errorMsg"]}")
                    throw IllegalStateException("API Failed: ${jsonHeader["errorMsg"]}\n$request")
                }
                json["content"]
            }
        }
    }

    /**
     * Reset mini wine database
     */
    fun resetWineDB() {
        miniWineDB.clear()
    }

    /**
     * Reset mini product database
     */
    fun resetProductDB() {
        miniProductDB.clear()
    }

    //////API WRAPPER FUNCTIONS//////
    /**
     * Find wine data of all vintages under a specific wine SEO
     * @param wineSEO wine SEO in String
     * @return Map of vintages and wine data
     */
    fun fetchWineData(wineSEO: String): Map<Short, VmDafdVintageMeta>? {
        var data: Map<Short, VmDafdVintageMeta>? = miniWineDB[wineSEO]
        if (data != null)
            return data

        data = fetchAPIWineData(wineSEO, null)
        if (data != null) {
            miniWineDB[wineSEO] = data
        }
        return data
    }

    /**
     * Emergency fetch one wine data using just product ID in case fetching through wine SEO fails
     * @param productID product ID of a product
     * @return map containing a single record (vintage and wine data)
     */
    fun fetchWineData(productID: Long): Map<Short, VmDafdVintageMeta>? {
        log.logAppend("Fetch wine data using ProductID: $productID")
        return fetchAPIWineData(null, productID)
    }

    /**
     * Find wine data of a specific vintage under a specific wine SEO
     * @param wineSEO wine SEO in String
     * @param vintageTag vintage in Short
     * @return A wine data (including vintage data)
     */
    fun fetchWineData(wineSEO: String, vintageTag: Short): VmDafdVintageMeta? {
        return if (miniWineDB.isEmpty()) {
            fetchWineData(wineSEO)
            fetchWineData(wineSEO, vintageTag)
        } else {
            if (miniWineDB.contains(wineSEO)) {
                if (miniWineDB[wineSEO]!!.contains(vintageTag))
                    miniWineDB[wineSEO]!![vintageTag]
                else
                    null
            } else {
                fetchWineData(wineSEO)
                fetchWineData(wineSEO, vintageTag)
            }
        }
    }

    /**
     * Find any product data including packages under it and return it
     * @param productID product ID in Long
     * @return information of the product(name and ID) and the map of packages
     */
    fun fetchProductData(productID: Long): Pair<ProductHolder, Map<Long, PackageHolder>>? {
        var data: Pair<ProductHolder, Map<Long, PackageHolder>>? = miniProductDB[productID]
        if (data != null)
            return data
        data = fetchAPIProductData(productID)
        if (data != null) {
            miniProductDB[productID] = data
        }
        return data
    }

    /**
     * @param productID
     * @param packageID
     * @return packageHolder with supplier SEO and package data
     */
    fun fetchProductData(productID: Long, packageID: Long): PackageHolder? {
        return if (miniProductDB.isEmpty()) {
            fetchProductData(productID)
            fetchProductData(productID, packageID)
        } else {
            if (miniProductDB.contains(productID)) {
                if (miniProductDB[productID]!!.second.containsKey(packageID))
                    miniProductDB[productID]!!.second[packageID]
                else
                    null
            } else {
                fetchProductData(productID)
                fetchProductData(productID, packageID)
            }
        }
    }

    //////API CALL FUNCTIONS//////
    /**
     * Turns the supplier SEO and its data (to be updated) into json format then calls the update API
     * @param supplierSeo supplier SEO
     * @param data list of wines to be converted
     * @return map of processed and rejected data of wines
     */
    fun processUpdate(supplierSeo: String, data: List<VmDafdPdpkVinImport>): Map<String, List<VmDafdPdpkVinImport>>? {
        val appToken = "kw6zi462bIZcuaCdeLtiNfG26TVygzI9wLSU4bISykId8EBrPCeb1WuxfmbdwWfZT9Pd"
        val adapter = moshi.adapter<List<VmDafdPdpkVinImport>>(Types.newParameterizedType(List::class.java, VmDafdPdpkVinImport::class.java)).indent("    ")
        val jsonString = adapter.toJson(data)

        val newJsonString = """{
            "supplierSeo":"$supplierSeo",
            "fileData":$jsonString
        }"""
        val resultString = httpCallRStr(newJsonString, updateUrl, appToken)
        if (resultString == "")
            return null
        println(resultString)
        val newAdapter: JsonAdapter<Map<String, List<VmDafdPdpkVinImport>>> = APICallFunc.moshi.adapter(Types.newParameterizedType(
                Map::class.java, String::class.java, Types.newParameterizedType(List::class.java, VmDafdPdpkVinImport::class.java)
        ))
        return newAdapter.fromJson(resultString)
    }

    /**
     * Turns the map of supplier SEO and their data into json format then calls the approve API
     * @param data map of supplier SEO and their wines to be converted
     * @return map of processed and rejected map of supplier SEO and list of wines
     */
    fun processApprove(data: Map<String, List<VmDafdPdpkVinImport>>): Map<String, Map<String, List<VmDafdPdpkVinImport>>>? {
        val appToken = "kw6zi462bIZcuaCdeLtiNfG26TVygzI9wLSU4bISykId8EBrPCeb1WuxfmbdwWfZT9Pd"
        val adapter = moshi.adapter<Map<String, List<VmDafdPdpkVinImport>>>(Types.newParameterizedType(
                Map::class.java, String::class.java, Types.newParameterizedType(List::class.java, VmDafdPdpkVinImport::class.java)
        )).indent("    ")
        val jsonString = adapter.toJson(data)
        val resultString = httpCallRStr(jsonString, approveUrl, appToken)
        if (resultString == "")
            return null
        println(resultString)
        val newAdapter = moshi.adapter<Map<String, Map<String, List<VmDafdPdpkVinImport>>>>(Types.newParameterizedType(
                Map::class.java, String::class.java,
                Types.newParameterizedType(Map::class.java, String::class.java,
                        Types.newParameterizedType(List::class.java, VmDafdPdpkVinImport::class.java))
        ))
        return newAdapter.fromJson(resultString)
    }

    /**
     * It receives json objects to map them into wine data object
     * @param it wine data (without winery data) in json object
     * @param wineryJsonObject winery data in json object
     * @return wine data that has both parameters information
     */
    private fun wineMapper(it: JsonObject, wineryJsonObject: JsonObject): VmDafdVintageMeta {
        val ratingList = mutableListOf<VmDafdWineRating>()
        val ratingArr = it["vmVintageScore4CriticsList"] as JsonArray<JsonObject>
        if (ratingArr.size != 0) {
            for (rating in ratingArr) {
                val ratingObj = VmDafdWineRating(
                        criticsSeo = rating["criticsSeoName"].toString(),
                        score = rating["scoreValStr"].toString()
                )
                ratingList.add(ratingObj)
            }
        }
        val varietalList = mutableListOf<VmDafdWineVarietal>()
        val varietalArr = it["vmVintageAttr4VarietyList"] as JsonArray<JsonObject>
        if (varietalArr.size != 0) {
            for (varietal in varietalArr) {
                val varietalObj = VmDafdWineVarietal(
                        varietalSeo = varietal["attrSeoName"].toString(),
                        numVal = varietal["attrValNum"]?.toString()?.toShort()
                )
                varietalList.add(varietalObj)
            }
        }

        var appellation: String? = null
        val appellationArr = it["vmVintageAttr4ClassificationList"] as JsonArray<JsonObject>?
        if (appellationArr != null)
            if (appellationArr.size != 0)
                appellation = appellationArr[0]["attrNameEng"].toString()

        return VmDafdVintageMeta(
                typeSeo = it["wineTypeSeoName"]?.toString(), winerySeo = it["winerySeoName"]?.toString(),
                winery = it["wineryNameEng"]?.toString(), wineryChs = it["wineryNameChs"]?.toString(),
                wineryCht = it["wineryNameCht"]?.toString(), wineryNote = wineryJsonObject["notePlainEng"]?.toString(),
                wineryNoteChs = wineryJsonObject["notePlainChs"]?.toString(), wineryNoteCht = wineryJsonObject["notePlainCht"]?.toString(),
                label = it["wineNameEng"]?.toString(), labelChs = it["labelChs"]?.toString(),
                labelCht = it["labelCht"]?.toString(), regionSeo = it["regionSeoName"]?.toString(),
                region = it["regionNameEng"]?.toString(), countrySeo = it["countrySeoName"]?.toString(),
                vinNote = it["vintageNotePlainEng"]?.toString(), vinNoteChs = it["vintageNotePlainChs"]?.toString(),
                vinNoteCht = it["vintageNotePlainCht"]?.toString(), alcohol = it["alcoholBps"]?.toString()?.toShort(),
                appellation = appellation, ratings = ratingList, varietals = varietalList
        )
    }

    /**
     * It calls API and fetches a single winery data
     * @param wineryId wineryId
     * @return wienry data in json object
     */
    private fun fetchWineryAPI(wineryId: Long): JsonObject {
        //TO CALL WINERY NOTE DATA
        val appToken2 = "YcsqIGOdzYHMPR3w1a80GdpVYoXekVRRfwNcAwlsL5zk"
        val jsonString2 = Klaxon().toJsonString(mapOf("keyId" to wineryId.toString()))
        return httpCall(jsonString2, getWineryNoteUrl, appToken2) as JsonObject
    }

    /**
     * It calls API and fetches wine data
     */
    private fun fetchAPIWineData(wineSEO: String?, productID: Long?): Map<Short, VmDafdVintageMeta>? {
        if(wineSEO == "")
            return null

        val appToken = "YcsqIGOdzYHMPR3w1a80GdpVYoXekVRRfwNcAwlsL5zk"
        val jsonString = if (wineSEO != null) {
            Klaxon().toJsonString(mapOf("wineSeo" to wineSEO))
        } else {
            Klaxon().toJsonString(mapOf("productId" to productID.toString()))
        }
        val jsonAny = (if (wineSEO != null)
            httpCall(jsonString, getWineDataUrl, appToken) ?: return null
        else
            httpCall(jsonString, getWineDataByProductIdUrl, appToken)) ?: return null

        val map = mutableMapOf<Short, VmDafdVintageMeta>()
        if (wineSEO != null) {
            val jsonArray = jsonAny as JsonArray<JsonObject>
            if (jsonArray.size == 0) return null
            var wineryId = jsonArray[0]["wineryId"].toString().toLong()
            val wineryJsonObject = fetchWineryAPI(wineryId)
            jsonArray.forEach {
                if (wineryId == (-1).toLong())
                    wineryId = it["wineryId"].toString().toLong()
                else if (wineryId != it["wineryId"].toString().toLong()) {
                    throw Exception("WineryId is different ($wineSEO, 1: $wineryId, 2:${it["wineryId"].toString().toLong()})")
                }

                val vinMeta = wineMapper(it, wineryJsonObject)

                map[it["vintageTag"].toString().toShort()] = vinMeta
            }
        } else {
            val jsonObject = jsonAny as JsonObject
            val wineryId = jsonObject["wineryId"].toString().toLong()
            val wineryJsonObject = fetchWineryAPI(wineryId)

            val vinMeta = wineMapper(jsonObject, wineryJsonObject)

            map[jsonObject["vintageTag"].toString().toShort()] = vinMeta
        }
        return map

    }

    /**
     * It calls API and fetches multiple package data under the product ID
     * @param productId Product ID of the product
     * @return Pair containing product holder(product ID and product name) and map of package ID to package holder(supplier Seo and package data)
     */
    private fun fetchAPIProductData(productId: Long): Pair<ProductHolder, Map<Long, PackageHolder>>? {
        val jsonString = Klaxon().toJsonString(mapOf("productId" to productId))
        val appToken = "YcsqIGOdzYHMPR3w1a80GdpVYoXekVRRfwNcAwlsL5zk"
        val jsonObject = httpCall(jsonString, getProductsUrl, appToken) as JsonObject? ?: return null

        val map: MutableMap<Long, PackageHolder> = mutableMapOf()

        val prodArray = jsonObject["vmProdpckgSupplierDetailList"] as JsonArray<JsonObject>
        prodArray.forEach {
            val pdpk = VmDafdPdpkPriceStock(
                    pckgType = it["pckgTypeCode"]?.toString(), pckgName = it["prodpckgNameEng"]?.toString(),
                    pckgNameChs = it["prodpckgNameChs"]?.toString(), pckgNameCht = it["prodpckgNameCht"]?.toString(),
                    hrDlvMax = it["hourMaxDelv"]?.toString()?.toLong(), hrDlvMin = it["hourMinDelv"]?.toString()?.toLong(),
                    qty = it["qtyForShop"]?.toString()?.toLong(), price = it["priceRegular"]?.toString()?.toBigDecimal()
            )

            val pdpkWithSupplierId = PackageHolder(it["supplierSeoName"]?.toString(), pdpk)

            map[it["prodpckgId"].toString().toLong()] = pdpkWithSupplierId
        }
        val productHolder = ProductHolder(productId, jsonObject["productNameEng"]?.toString())
        return Pair(productHolder, map)
    }

    /**
     * It fetches list of all wine SEOs in database
     * @return list of all wine SEOs
     */
    fun fetchAllWine(): List<String>? {
        val appToken = "YcsqIGOdzYHMPR3w1a80GdpVYoXekVRRfwNcAwlsL5zk"
        val jsonArray = httpCall("{}", getWineSeoListUrl, appToken) as JsonArray<JsonObject>? ?: return null

        val list: MutableList<String> = mutableListOf()
        jsonArray.forEach {
            if (it["wineSeo"] != null) {
                list.add(it["wineSeo"].toString())
            }
        }
        return list
    }

    /**
     * Helper class for moshi to read/write BigDecimal object
     */
    class BigDecimalAdapter {
        @ToJson
        fun toJson(bigDecimal: BigDecimal): String {
            return bigDecimal.toString()
        }

        @FromJson
        fun fromJson(bigDecimalString: String): BigDecimal {
            return bigDecimalString.toBigDecimal()
        }
    }
}