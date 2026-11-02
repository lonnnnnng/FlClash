package com.lonnnnnng.flclash.data

import com.lonnnnnng.flclash.core.CoreIpResponse
import com.lonnnnnng.flclash.core.CoreRouteStamp
import org.junit.Assert.*
import org.junit.Test

class IpInformationTest {
    @Test fun outboundParsesAllEightSources() {
        val bodies = listOf(
            "ip=8.8.8.8\nloc=US\n", """{"ip":"8.8.8.8","country_code":"US"}""",
            """{"ip":"8.8.8.8","country":"US"}""", """{"ip":"8.8.8.8","country_code":"US"}""",
            """{"ip":"8.8.8.8","country_code":"US"}""", """{"ip":"8.8.8.8","country":"US"}""",
            """{"ip":"8.8.8.8","location":{"country_code":"US"}}""", """{"ip":"8.8.8.8","cc":"us"}""",
        )
        outboundIpSources.zip(bodies).forEach { (url, body) ->
            assertEquals(OutboundIpInfo("8.8.8.8", "US"), parseOutboundIp(response(body, url)))
        }
    }

    @Test fun rejectsHostnamesPrivateAddressesAndInvalidPayloads() {
        listOf("example.com", "127.0.0.1", "10.0.0.1", "192.168.1.1", "100.64.0.1", "0.0.0.0",
            "224.0.0.1", "::1", "fc00::1", "fe80::1", "8.8.8.8/path", "999.1.1.1", "1234").forEach {
            assertFalse(it, isPublicIpLiteral(it))
        }
        assertTrue(isPublicIpLiteral("2606:4700:4700::1111"))
        assertTrue(isPublicIpLiteral("1.1.1.1"))
        assertThrows(Exception::class.java) { parseOutboundIp(response("{}", outboundIpSources[1])) }
        assertThrows(Exception::class.java) { parseOutboundIp(response("ip=8.8.8.8\nloc=USA", outboundIpSources[0])) }
        assertThrows(Exception::class.java) { parseOutboundIp(response("{}", "https://unknown.invalid")) }
    }

    @Test fun qualityParsesAllSourcesAndPrioritizesHosting() {
        val fixtures = mapOf(
            IpQualitySource.IDENT_ME to """{"ip":"8.8.8.8","type":"hosting","aso":"Example","asn":123}""",
            IpQualitySource.IP_API_COM to """{"status":"success","hosting":true,"mobile":true,"org":"Example","as":"AS123 Example"}""",
            IpQualitySource.IP_QUERY to """{"risk":{"is_datacenter":true,"is_mobile":true},"isp":{"org":"Example","asn":"AS123"}}""",
            IpQualitySource.IP_LOCATE to """{"privacy":{"is_hosting":true},"company":{"type":"isp","name":"Example"},"asn":{"asn":123}}""",
            IpQualitySource.IP_API_IS to """{"is_datacenter":true,"company":{"type":"isp","name":"Example"},"asn":{"asn":123}}""",
            IpQualitySource.PROXY_CHECK to """{"status":"ok","8.8.8.8":{"network":{"type":"Hosting","organisation":"Example","asn":"AS123"}}}""",
        )
        fixtures.forEach { (source, body) ->
            val result = parseIpQuality(source, "8.8.8.8", response(body))
            assertEquals(source.name, IpType.HOSTING, result.quality.type)
            assertEquals("Example", result.quality.organization)
            assertEquals(123L, result.quality.asn)
            assertEquals("一般", result.quality.levelLabel)
            assertFalse(result.inferred)
        }
    }

    @Test fun failedSourcesDoNotInventGoodQuality() {
        fun failure(source: IpQualitySource, body: String, status: Int = 200) = assertThrows(IpSourceException::class.java) {
            parseIpQuality(source, "8.8.8.8", response(body, status = status))
        }.failure
        assertEquals(IpSourceFailure.IP_MISMATCH, failure(IpQualitySource.IDENT_ME, """{"ip":"1.1.1.1","type":"isp"}"""))
        assertEquals(IpSourceFailure.NO_TYPE, failure(IpQualitySource.IP_QUERY, "{}"))
        assertEquals(IpSourceFailure.RATE_LIMITED, failure(IpQualitySource.PROXY_CHECK, """{"status":"denied"}"""))
        assertEquals(IpSourceFailure.RATE_LIMITED, failure(IpQualitySource.IDENT_ME, "{}", 429))
        assertEquals(IpSourceFailure.NO_TYPE, failure(IpQualitySource.IP_LOCATE, "{}"))
    }

    @Test fun inferredResidentialAndRiskLevelsFollowFlutter() {
        val answer = parseIpQuality(IpQualitySource.IP_API_COM, "8.8.8.8", response("""{"status":"success","hosting":false,"mobile":false,"proxy":true}"""))
        assertTrue(answer.inferred)
        assertEquals(IpType.RESIDENTIAL, answer.quality.type)
        assertEquals("良好", answer.quality.levelLabel)
        assertEquals("高风险", answer.quality.copy(isTor = true).levelLabel)
        assertEquals("高风险", answer.quality.copy(isAbuser = true).levelLabel)
        assertEquals("良好", answer.quality.copy(isVpn = true).levelLabel)
    }

    private fun response(body: String, url: String = "", status: Int = 200) =
        CoreIpResponse(url, body, "", status, CoreRouteStamp(1, 1))
}
