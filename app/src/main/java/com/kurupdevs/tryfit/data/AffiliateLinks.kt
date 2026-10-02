package com.kurupdevs.tryfit.data

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Affiliate deep-link builders. Used by Worker D's product detail
 * ("buy" handoff). Query = brand + name, URL-encoded. ₹ market retailers only.
 */
object AffiliateLinks {

    private fun encode(query: String): String =
        URLEncoder.encode(query, StandardCharsets.UTF_8.toString())

    private fun query(brand: String, name: String): String = encode("$brand $name")

    fun myntra(brand: String, name: String): String =
        "https://www.myntra.com/${query(brand, name)}"

    fun flipkart(brand: String, name: String): String =
        "https://www.flipkart.com/search?q=${query(brand, name)}"

    fun amazonIn(brand: String, name: String): String =
        "https://www.amazon.in/s?k=${query(brand, name)}"

    fun ajio(brand: String, name: String): String =
        "https://www.ajio.com/search/?text=${query(brand, name)}"

    fun meesho(brand: String, name: String): String =
        "https://www.meesho.com/search?q=${query(brand, name)}"

    data class Retailer(val label: String, val build: (brand: String, name: String) -> String)

    val all: List<Retailer> = listOf(
        Retailer("Myntra", ::myntra),
        Retailer("Flipkart", ::flipkart),
        Retailer("Amazon.in", ::amazonIn),
        Retailer("Ajio", ::ajio),
        Retailer("Meesho", ::meesho)
    )
}
