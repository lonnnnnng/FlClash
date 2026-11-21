package com.lonnnnnng.flclash.ui

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.*
import org.junit.Test

class UriDisplayTransformationTest {
    @Test fun uriOffsetsRoundTripAcrossHostFilenameUnicodeAndEscapedQuery() {
        for (raw in listOf("", "https://example.invalid/country.mmdb", "https://a.example/中文/🧩.json?x=a%2Fb&empty=", "https://host/"+"x".repeat(4096)+".yaml")) {
            val input=AnnotatedString(raw);val output=UriDisplayTransformation.filter(input)
            assertEquals(raw,input.text)
            assertEquals(raw,output.text.text.replace("\u2060",""))
            for (offset in 0..raw.length) assertEquals(offset,output.offsetMapping.transformedToOriginal(output.offsetMapping.originalToTransformed(offset)))
            val mapped=(0..output.text.length).map(output.offsetMapping::transformedToOriginal)
            assertTrue(mapped.zipWithNext().all{(a,b)->b>=a && b-a<=1})
            assertEquals(raw.length,mapped.last())
        }
    }
}
