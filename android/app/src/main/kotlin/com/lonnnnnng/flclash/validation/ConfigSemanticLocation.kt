package com.lonnnnnng.flclash.validation

import com.lonnnnnng.flclash.service.YamlConfigCodec
import com.lonnnnnng.flclash.service.YamlErrorLocation

/** 作者 long：位置解析沿用服务层安全YAML工具，应用模块不另接一套解析依赖。 */
fun ConfigSemanticException.location(content: String): YamlErrorLocation? =
    YamlConfigCodec.semanticErrorLocation(content, section, index)
