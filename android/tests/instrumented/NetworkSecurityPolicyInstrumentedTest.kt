package com.lonnnnnng.flclash.data

import android.security.NetworkSecurityPolicy
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSecurityPolicyInstrumentedTest {
    @Test
    fun userProvidedHttpHostsAreNotRestrictedByDomainAllowlist() {
        val policy = NetworkSecurityPolicy.getInstance()

        assertTrue("应用全局应允许用户配置的 HTTP 来源", policy.isCleartextTrafficPermitted())
        assertTrue("普通主机名不应被域名清单拦截", policy.isCleartextTrafficPermitted("subscriptions.example"))
        assertTrue("局域网地址不应被域名清单拦截", policy.isCleartextTrafficPermitted("192.0.2.1"))
    }
}
