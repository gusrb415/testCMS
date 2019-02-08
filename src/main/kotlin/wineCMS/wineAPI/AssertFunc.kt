package wineCMS.wineAPI

import log
import wineCMS.customPojo.*
import wineCMS.pojo.*
import java.math.BigDecimal
import java.math.RoundingMode

class AssertFunc {
    /**
     * It checks on the vintage and package count of this specific wine data
     * @param summary summary data containing all days expected counts for each wine and vintage
     * @param day to get data of this day in summary
     * @param label to get data of this label on this day in summary
     * @param vintage to get data of this vintage of the label of the day in summary
     * @param wineSEO wineSeo to retrieve value of wine data to get number of vintages
     * @param productID productID to retrieve value of package data to get number of packages
     * @return true if the expected and actual count are the same
     */
    fun checkVintagePackageCount(summary: ExcelSummaryList, day: Int, name: String, vintage: Short?, wineSEO: String, productID: Long): Boolean {
        //CHECK VINTAGE COUNT HAS INCREASED AS MUCH AS EXPECTED
        var count = summary.get(day, name)?.vintageCount ?: 0
        var comparison = vintageCountCheck(wineSEO, count)
        if (!comparison) {
            return false
        }

        //CHECK PACKAGE COUNT HAS INCREASED AS MUCH AS EXPECTED
        count = summary.get(day, name, vintage ?: 0.toShort())?.packageCount ?: 0
        comparison = packageCountCheck(productID, count)
        return comparison
    }

    /**
     * It compares the wine data received with the wine data called using API in package level
     * @param supplierSeo to compare if the wine has same supplier SEO as requested
     * @param wine to compare if this wine data is same as the retrieved data from database
     * @return true if all the data are equal
     */
    fun packageLevelComparison(supplierSeo: String, wine: ExcelFinalWine): Boolean {
        val productID = wine.vinImport.productId ?: return false
        val packageID = wine.vinImport.prodpckgId ?: return false
        val expected = PackageHolder(supplierSeo, wine.vinImport.pdpk)
        if(wine.disappear)
            expected.pdpk.qty = 0
        val resultPackageInfo = api.fetchProductData(productID, packageID)
        if (resultPackageInfo == null) {
            log.logAppend("Retrieving Package data failed")
            return false
        }

        if (resultPackageInfo.supplierSeo != supplierSeo) {
            log.logAppend("Supplier SEO for this package API is wrong")
            return false
        }

        val comparison = packageLevelAttributeAssert(expected, resultPackageInfo)
        if (!comparison)
            log.logAppend("Package level attribute test failed")
        return comparison
    }

    /**
     * It compares the wine data received with the wine data called using API in vintage level
     * @param wine vintage data to be compared if the wine is new wine (as well as to obtain the vintage tag)
     * @param wineSEO wine SEO to retrieve the wine data using the API
     * @param expected vintage data to be compared if the wine is not the new wine
     * @return true if the vintage data compared is the same
     */
    fun vintageLevelComparison(wine: ExcelFinalWine, wineSEO: String, expected: VmDafdVintageMeta): Boolean {
        val vintage = wine.vinImport.pdpk.vintageTag!!
        //THE VINTAGE DATA SHOULD MATCH IF NEW VINTAGE
        val getWineInfoResult = api.fetchWineData(wineSEO, vintage)
                ?: api.fetchWineData(wine.vinImport.productId!!)!![vintage]!!
        return if (wine.newVint) {
            val compareVintageData = vintageLevelAttributeAssert(wine.vinImport.vin!!, getWineInfoResult)
            if (!compareVintageData)
                log.logAppend("Vintage data for $wineSEO, vintage $vintage is different from database")
            compareVintageData
        } else {
            vintageLevelAttributeAssert(expected, getWineInfoResult)
        }
    }

    /**
     * It compares the wine expected with the database count if both of them are either sold out or not
     * @param wine wine data to be compared
     * @return true if the wine expected sold out and actual is the same
     */
    fun checkSoldOut(wine: ExcelFinalWine): Boolean {
        val productID = wine.vinImport.productId!!
        val packageID = wine.vinImport.prodpckgId!!

        //CHECK SOLD OUT PACKAGES
        val soldOutCheck = packageSoldOutCheck(productID, packageID)
        val expectedBoolean = wine.vinImport.pdpk.qty == 0.toLong() || wine.disappear
        if (expectedBoolean != soldOutCheck)
            log.logAppend("Package with pacakge ID: $packageID was expected ${if (!expectedBoolean) "to be in stock but it was sold out" else "to be sold out but was in stock"}")
        return expectedBoolean == soldOutCheck
    }

    /////////////////////////////////////////////API RUN FUNCTIONS/////////////////////////////////////////////
    private val api = APICallFunc()

    /**
     * It compares package level attribute data including supplier SEO of the package
     * @param pack1 first package holder (supplierSEO, pdpk)
     * @param pack2 second package holder (supplierSEO, pdpk)
     * @return true if all of the attributes are the same
     */
    fun packageLevelAttributeAssert(pack1: PackageHolder, pack2: PackageHolder): Boolean {
        val values = mapOf(
                "supplierSeo" to Pair(pack1.supplierSeo, pack2.supplierSeo),
                "pckgType" to Pair(pack1.pdpk.pckgType, pack2.pdpk.pckgType),
                "pckgName" to Pair(pack1.pdpk.pckgName, pack2.pdpk.pckgName),
//                "pckgNameChs" to Pair(pack1.pdpk.pckgNameChs, pack2.pdpk.pckgNameChs),
//                "pckgNameCht" to Pair(pack1.pdpk.pckgNameCht, pack2.pdpk.pckgNameCht),
                "hrDlvMax" to Pair(pack1.pdpk.hrDlvMax, pack2.pdpk.hrDlvMax),
                "hrDlvMin" to Pair(pack1.pdpk.hrDlvMin, pack2.pdpk.hrDlvMin),
                "qty" to Pair(pack1.pdpk.qty, pack2.pdpk.qty),
                "price" to Pair(pack1.pdpk.price, pack2.pdpk.price)
        )
        var attributeString = ""
        var comparisonString = ""
        values.forEach {
            if(it.value.first is BigDecimal){
                val firstPrice = it.value.first as BigDecimal
                val secondPrice = it.value.second as BigDecimal
                if(firstPrice.setScale(2, RoundingMode.HALF_UP) != secondPrice.setScale(2, RoundingMode.HALF_UP)) {
                    attributeString += if (attributeString != "") ", ${it.key}" else it.key
                    comparisonString += "\n${it.key}: $firstPrice---$secondPrice"
                }
            }
            else if (it.value.first != it.value.second) {
                attributeString += if (attributeString != "") ", ${it.key}" else it.key
                comparisonString += "\n${it.key}: ${it.value.first}---${it.value.second}"
            }
        }

        if (attributeString != "") {
            log.logAppend("Package level attributes are different($attributeString)$comparisonString")
            return false
        }
        return true
    }

    /**
     * It compares product level attribute data
     * @param pack1 first product holder (productName, productId)
     * @param pack2 second product holder (productName, productId)
     * @return true if all of the attributes are the same
     */
    fun productLevelAttributeAssert(pack1: ProductHolder, pack2: ProductHolder): Boolean {
        val values = mapOf(
                "productName" to Pair(pack1.productName, pack2.productName),
                "productId" to Pair(pack1.productId, pack2.productId)
        )

        var attributeString = ""
        var comparisonString = ""
        values.forEach {
            if (it.value.first != it.value.second) {
                attributeString += if (attributeString != "") ", ${it.key}" else it.key
                comparisonString += "\n${it.key}: ${it.value.first}---${it.value.second}"
            }
        }

        if (attributeString != "") {
            log.logAppend("product level attributes are different($attributeString)$comparisonString")
            return false
        }
        return true
    }

    /**
     * It compares if the two of the rating lists are the same (the order does not matter)
     * @param rating1 first rating list to be compared
     * @param rating2 second rating list to be compared
     * @return true if rating2 has all same ratings of rating1
     */
    private fun ratingAttributeAssert(rating1: List<VmDafdWineRating>?, rating2: List<VmDafdWineRating>?): Boolean {
        if (rating1 == null && rating2 == null) {
            return true
        } else if (rating1 == null && rating2!!.isEmpty()){
            return true
        } else if(rating1 == null) {
            log.logAppend("first rating list is null")
            return false
        } else if (rating2 == null) {
            log.logAppend("second rating list is null")
            return false
        }

        if (rating1.size != rating2.size) {
            log.logAppend("first rating list and second rating list have different size")
            return false
        }

        var fullCheck = true
        for (i in 0 until rating1.size) {
            val check = mutableMapOf(
                    "criticsSeo" to false,
                    "score" to false
            )

            for (j in 0 until rating2.size) {
                if (rating1[i].criticsSeo == rating2[j].criticsSeo)
                    check["criticsSeo"] = true

                if (rating1[i].score == rating2[j].score)
                    check["score"] = true
            }

            var finalString = ""

            check.forEach {
                if (!it.value) finalString += if (finalString != "") ", ${it.key}" else it.key
            }

            if (check.containsValue(false)) {
                log.logAppend("$i rating details are different ($finalString)")
                fullCheck = false
            }
        }

        return fullCheck
    }

    /**
     * It compares if the two of the varietal lists are the same (the order does not matter)
     * @param varietal1 first varietal list to be compared
     * @param varietal2 second varietal list to be compared
     * @return true if varietal2 has all same varietals of varietal1
     */
    private fun varietalAttributeAssert(varietal1: List<VmDafdWineVarietal>?, varietal2: List<VmDafdWineVarietal>?): Boolean {
        if (varietal1 == null && varietal2 == null) {
            return true
        } else if (varietal1 == null && varietal2!!.isEmpty()) {
            return true
        } else if (varietal1 == null) {
                log.logAppend("first varietal list is null")
                return false
        } else if (varietal2 == null) {
            log.logAppend("second varietal list is null")
            return false
        }

        if (varietal1.size != varietal2.size) {
            log.logAppend("first varietal list and second varietal list have different size")
            return false
        }

        var fullCheck = true
        for (i in 0 until varietal1.size) {
            val check = mutableMapOf(
                    "varietalSeo" to false,
                    "numVal" to false
            )

            for (j in 0 until varietal2.size) {
                if (varietal1[i].varietalSeo == varietal2[j].varietalSeo)
                    check["varietalSeo"] = true

                if (varietal1[i].numVal == varietal2[j].numVal)
                    check["numVal"] = true
            }

            var finalString = ""

            check.forEach {
                if (!it.value) finalString += if (finalString != "") ", ${it.key}" else it.key
            }

            if (check.containsValue(false)) {
                log.logAppend("$i varietal details are different ($finalString)")
                fullCheck = false
            }
        }

        return fullCheck
    }

    /**
     * It compares two vintage data and returns a boolean
     * @param vin1 first vintage data to be compared
     * @param vin2 second vintage data to be compared
     * @return true if both vintage data are the same
     */
    fun vintageLevelAttributeAssert(vin1: VmDafdVintageMeta, vin2: VmDafdVintageMeta): Boolean {
        val values = mapOf(
                "vinNote" to Pair(vin1.vinNote, vin2.vinNote),
//                "vinNoteChs" to Pair(vin1.vinNoteChs, vin2.vinNoteChs),
//                "vinNoteCht" to Pair(vin1.vinNoteCht, vin2.vinNoteCht),
                "alcohol" to Pair(vin1.alcohol, vin2.alcohol),
                "appellation" to Pair(vin1.appellation, vin2.appellation)
        )
        val secondMap = mapOf(
                "ratings" to (!ratingAttributeAssert(vin1.ratings, vin2.ratings)),
                "varietals" to (!varietalAttributeAssert(vin1.varietals, vin2.varietals))
        )

        var attributeString = ""
        var comparisonString = ""
        values.forEach {
            if (it.value.first != it.value.second) {
                attributeString += if (attributeString != "") ", ${it.key}" else it.key
                comparisonString += "\n${it.key}: ${it.value.first}---${it.value.second}"
            }
        }

        secondMap.forEach{
            if (it.value) {
                attributeString += if (attributeString != "") ", ${it.key}" else it.key
            }
        }

        if (attributeString != "") {
            log.logAppend("vintage level attributes are different($attributeString)$comparisonString")
            return false
        }
        return true
    }

    /**
     * It compares two wine data and returns a boolean
     * @param wineInfoA first wine data to be compared
     * @param wineInfoB second wine data to be compared
     * @return true if both wine data are the same
     */
    fun wineLevelAttributeAssert(wineInfoA: VmDafdVintageMeta, wineInfoB: VmDafdVintageMeta): Boolean {
        val values = mapOf(
                "typeSeo" to Pair(wineInfoA.typeSeo, wineInfoB.typeSeo),
                "winerySeo" to Pair(wineInfoA.winerySeo, wineInfoB.winerySeo),
                "winery" to Pair(wineInfoA.winery, wineInfoB.winery),
//                "wineryChs" to Pair(wineInfoA.wineryChs, wineInfoB.wineryChs),
//                "wineryCht" to Pair(wineInfoA.wineryCht, wineInfoB.wineryCht),
                "wineryNote" to Pair(wineInfoA.wineryNote, wineInfoB.wineryNote),
//                "wineryNoteChs" to Pair(wineInfoA.wineryNoteChs, wineInfoB.wineryNoteChs),
//                "wineryNoteCht" to Pair(wineInfoA.wineryNoteCht, wineInfoB.wineryNoteCht),
                "label" to Pair(wineInfoA.label?:"", wineInfoB.label),
//                "labelCht" to Pair(wineInfoA.labelCht, wineInfoB.labelCht),
//                "labelChs" to Pair(wineInfoA.labelChs, wineInfoB.labelChs),
                "regionSeo" to Pair(wineInfoA.regionSeo, wineInfoB.regionSeo),
                "region" to Pair(wineInfoA.region, wineInfoB.region),
                "countrySeo" to Pair(wineInfoA.countrySeo, wineInfoB.countrySeo)
        )

        var attributeString = ""
        var comparisonString = ""
        values.forEach {
            if (it.value.first != it.value.second) {
                attributeString += if (attributeString != "") ", ${it.key}" else it.key
                comparisonString += "\n${it.key}: ${it.value.first}---${it.value.second}"
            }
        }
        if (attributeString != "") {
            log.logAppend("wine level attributes are different($attributeString)$comparisonString")
            return false
        }
        return true
    }

    /**
     * It compares the expected wine count and the current total wine count in the database
     * @param expected expected total wine count
     * @return true if the expected and the actual are the same
     */
    fun wineCountCheck(expected: Int): Boolean {
        val count = api.fetchAllWine()?.size ?: 0
        if (count != expected) {
            log.logAppend("The total count of wine expected ($expected) is different from actual ($count)")
            return false
        }
        return true
    }

    /**
     * It compares the expected vintage count and the current vintage count of the wine in the database
     * @param wineSeo the wine Seo of the wine to be used to call the API
     * @param expected expected vintage count
     * @return true if the expected and the actual are the same
     */
    private fun vintageCountCheck(wineSeo: String, expected: Int): Boolean {
        val count = api.fetchWineData(wineSeo)?.size ?: 0
        if (count != expected) {
            log.logAppend("The vintage count for this wine is wrong expected: $expected, actual: $count")
            return false
        }
        return true
    }

    /**
     * It compares the expected package count and the current package count of the product in the database
     * @param productId the product ID of the product to be used to call the API
     * @param expected expected package count
     * @return true if the expected and the actual are the same
     */
    private fun packageCountCheck(productId: Long, expected: Int): Boolean {
        val count = api.fetchProductData(productId)?.second?.size ?: 0
        if (count != expected) {
            log.logAppend("The package count for this wine is wrong expected: $expected, actual: $count")
            return false
        }
        return true
    }

    /**
     * It checks if the package is sold out in the database
     * @param productId product ID of the package
     * @param packageId package ID of the package
     * @return true if the package is sold out
     */
    private fun packageSoldOutCheck(productId: Long, packageId: Long): Boolean {
        val packageQty = api.fetchProductData(productId, packageId)?.pdpk?.qty
        if (packageQty == null) {
            log.logAppend("No such package is found with product ID: $productId, package ID: $packageId")
            return false
        }
        if (packageQty == 0.toLong())
            return true
        return false
    }
}