package wineCMS.wineAPI

object Logger {
    private var sb = StringBuilder()

    /**
     * Reset the string builder and print the starting message
     * @param header title of the beginning case
     * @param msg explanation of the beginning case
     */
    fun logCaseBegin(header: String, msg: String) {
        reset()
        logPrint(header, msg)
    }

    /**
     * Print header and message without storing message
     * @param header title of the beginning case
     * @param msg explanation of the beginning case
     */
    fun log(header: String, msg: String) {
        logPrint(header, msg)
    }

    /**
     * Print and store the message
     * @param msg explanation of the case
     */
    fun logAppend(msg: String) {
        logPrint(sb, msg)
    }

    /**
     * Print all the stored stored data with header
     * @param header title of the summary
     */
    fun outputAppend(header: String) {
        logPrint(header, sb.toString())
    }

    /**
     * Reset the string builder object
     */
    private fun reset() {
        sb = StringBuilder()
    }

    /**
     * Just print the header and the message
     * @param header title of the case
     * @param msg explanation of the case
     */
    private fun logPrint(header: String, msg: String) {
        println("$header: $msg\n")
    }

    /**
     * Print and store the message
     * @param sb string builder to store messages
     * @param msg explanation of the case
     */
    private fun logPrint(sb: StringBuilder, msg: String) {
        sb.append(msg).append(";")
        println(msg)
    }
}
