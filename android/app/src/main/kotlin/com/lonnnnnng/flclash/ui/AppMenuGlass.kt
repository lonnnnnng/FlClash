package com.lonnnnnng.flclash.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlin.math.*

internal data class AppMenuBackdrop(val raw: Bitmap, val frosted: Bitmap, val scaleX: Float, val scaleY: Float)

internal fun DrawScope.drawAppMenuFrost(backdrop: AppMenuBackdrop, offset: Offset, paintScale: Float = 1f) {
    drawImage(backdrop.frosted.asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset((offset.x / paintScale).roundToInt(), (offset.y / paintScale).roundToInt()),
        dstSize = androidx.compose.ui.unit.IntSize((backdrop.raw.width / backdrop.scaleX / paintScale).roundToInt(), (backdrop.raw.height / backdrop.scaleY / paintScale).roundToInt()))
}

/** 作者 long：菜单只持有一帧降采样背景；把12dp模糊移出逐帧着色，避免多层卡片同时展开时反复扫描整屏。 */
internal suspend fun prepareAppMenuBackdrop(bitmap: Bitmap, density: Float): AppMenuBackdrop = withContext(Dispatchers.Default) {
    val processing = currentCoroutineContext()
    val divisor = max(4f, max(bitmap.width, bitmap.height) / 2048f).coerceAtMost(8f)
    val raw = Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width / divisor).roundToInt()), max(1, (bitmap.height / divisor).roundToInt()), true)
    val pixels = IntArray(raw.width * raw.height)
    raw.getPixels(pixels, 0, raw.width, 0, 0, raw.width, raw.height)
    val scratch = IntArray(pixels.size)
    val radius = max(1, (12f * density / divisor - .5f).roundToInt())
    // 作者 long：三次盒式卷积的总方差接近目标高斯；边界钳位与BitmapShader一致，窗口边缘不会出现黑色采样带。
    fun pass(input: IntArray, output: IntArray, horizontal: Boolean) {
        val length = if (horizontal) raw.width else raw.height
        val lines = if (horizontal) raw.height else raw.width
        val count = radius * 2 + 1
        for (line in 0 until lines) {
            if (line % 16 == 0) processing.ensureActive()
            fun index(at: Int) = if (horizontal) line * raw.width + at.coerceIn(0, length - 1) else at.coerceIn(0, length - 1) * raw.width + line
            var a = 0; var r = 0; var g = 0; var b = 0
            fun add(pixel: Int, sign: Int) { a += (pixel ushr 24) * sign; r += (pixel ushr 16 and 255) * sign; g += (pixel ushr 8 and 255) * sign; b += (pixel and 255) * sign }
            for (at in -radius..radius) add(input[index(at)], 1)
            for (at in 0 until length) {
                output[index(at)] = (a / count shl 24) or (r / count shl 16) or (g / count shl 8) or (b / count)
                add(input[index(at - radius)], -1); add(input[index(at + radius + 1)], 1)
            }
        }
    }
    repeat(3) { pass(pixels, scratch, true); pass(scratch, pixels, false) }
    val frosted = Bitmap.createBitmap(pixels, raw.width, raw.height, Bitmap.Config.ARGB_8888)
    AppMenuBackdrop(raw, frosted, raw.width.toFloat() / bitmap.width, raw.height.toFloat() / bitmap.height)
}

/** 作者 long：RuntimeShader仅在API33以上创建；旧系统继续使用同一份预模糊背景，不让新API阻断普通菜单。 */
internal class AppMenuGlassRenderer {
    private val optics by lazy { if (Build.VERSION.SDK_INT >= 33) Optics() else null }
    fun draw(scope: DrawScope, frame: AppMenuMorphFrame, backdrop: AppMenuBackdrop, offset: Offset, tint: Color, dark: Boolean, refraction: Float = 60f, paintScale: Float = 1f, amount: Float = 1f): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        optics?.draw(scope, frame, backdrop, offset, tint, dark, refraction, paintScale, amount)
        return optics != null
    }

    @RequiresApi(33)
    private class Optics {
        private val shader = RuntimeShader(MENU_GLASS_SHADER)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = this@Optics.shader }
        private var bound: AppMenuBackdrop? = null
        fun draw(scope: DrawScope, frame: AppMenuMorphFrame, backdrop: AppMenuBackdrop, offset: Offset, tint: Color, dark: Boolean, refraction: Float, paintScale: Float, amount: Float) {
            if (bound !== backdrop) {
                shader.setInputShader("raw", BitmapShader(backdrop.raw, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setFilterMode(BitmapShader.FILTER_MODE_LINEAR) })
                shader.setInputShader("frost", BitmapShader(backdrop.frosted, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setFilterMode(BitmapShader.FILTER_MODE_LINEAR) })
                bound = backdrop
            }
            val rect = frame.platter
            val drop = frame.droplet?.takeUnless { it.isEmpty } ?: rect
            val radius = frame.radius.coerceIn(.001f, rect.size.minDimension.coerceAtLeast(.002f) / 2)
            shader.setFloatUniform("box", rect.center.x, rect.center.y, rect.width / 2, rect.height / 2)
            shader.setFloatUniform("drop", drop.center.x, drop.center.y, drop.width / 2, drop.height / 2)
            shader.setFloatUniform("geometry", radius, if (frame.droplet != null) frame.blend else 0f, scope.density, if (dark) 1f else 0f)
            shader.setFloatUniform("refraction", refraction)
            shader.setFloatUniform("paintScale", paintScale)
            shader.setFloatUniform("amount", amount.coerceIn(0f, 1f))
            shader.setFloatUniform("sampling", -offset.x, -offset.y, backdrop.scaleX, backdrop.scaleY)
            shader.setFloatUniform("tint", tint.red, tint.green, tint.blue, if (dark) .63f else .55f)
            val x = appGlassCurve(rect.width / 2, radius); val y = appGlassCurve(rect.height / 2, radius)
            shader.setFloatUniform("curveX", x.copyOfRange(0, 4)); shader.setFloatUniform("curveY", y.copyOfRange(0, 4))
            shader.setFloatUniform("centers", x[4], x[5], y[4], y[5])
            val pill = appGlassCurve(max(drop.width, drop.height) / 2, min(drop.width, drop.height) / 2)
            shader.setFloatUniform("pillCurve", pill.copyOfRange(0, 4)); shader.setFloatUniform("pillCenter", pill[4], pill[5])
            scope.drawContext.canvas.nativeCanvas.drawPath(frame.outline().asAndroidPath(), paint)
        }
    }
}

// 作者 long：光学参数移植固定Flutter liquid_glass.frag；菜单原材质无色散/雾化，不能为了“玻璃感”额外制造彩边。
internal const val MENU_GLASS_SHADER = """
uniform shader raw;
uniform shader frost;
uniform float4 box;
uniform float4 drop;
uniform float4 geometry;
uniform float4 sampling;
uniform float4 tint;
uniform float4 curveX;
uniform float4 curveY;
uniform float4 centers;
uniform float refraction;
uniform float paintScale;
uniform float amount;
uniform float4 pillCurve;
uniform float2 pillCenter;
const float3 luma = float3(.2126,.7152,.0722);
float distanceBox(float2 p,float2 halfSize,float radius) {
    float2 q=abs(p)-halfSize+radius;
    return length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius;
}
float2 normalBox(float2 p,float2 halfSize,float radius) {
    float2 q=abs(p)-halfSize+radius;
    float2 n=q.x>0.0&&q.y>0.0?normalize(q):(q.x>q.y?float2(1,0):float2(0,1));
    return n*float2(p.x<0.0?-1:1,p.y<0.0?-1:1);
}
float octant(float2 p,float a,float4 curve,float2 center) {
    float2 v=p-center,j=curve.yz-center;
    if(j.x*v.y<j.y*v.x) return curve.w-length(v);
    float2 s=max(p,0.0)/max(a,.001);
    float major=max(max(s.x,s.y),.0001);
    float norm=major*pow(1.0+pow(min(s.x,s.y)/major,curve.x),1.0/curve.x);
    return a*(1.0-norm)/max(length(pow(s/max(norm,.0001),float2(curve.x-1.0))),.0001);
}
float insetCurve(float2 p) {
    if(curveX.x<2.0) return min(box.z-abs(p.x),box.w-abs(p.y));
    float2 q=abs(p); float c=box.z-box.w;
    return q.y+c>=q.x?octant(float2(q.x,q.y+c),box.z,curveX,centers.xy):octant(float2(q.y,q.x-c),box.w,curveY,centers.zw);
}
half4 sampleRaw(float2 p) { return raw.eval((p*paintScale+sampling.xy)*sampling.zw); }
half4 sampleFrost(float2 p) { return frost.eval((p*paintScale+sampling.xy)*sampling.zw); }
float band(float a,float b,float start,float end,float lit) { return max(min(b,end)-max(a,start),0.0)*lit; }
float shine(float edge,float unit) {
    float a=max(edge/unit-.5/unit,0.0), b=edge/unit+.5/unit;
    return (band(a,b,0.0,1.0/3.0,1.0)+band(a,b,1.0/3.0,2.0/3.0,.7)+band(a,b,2.0/3.0,1.0,.47)
        +band(a,b,1.0,5.0/3.0,.18)+band(a,b,5.0/3.0,7.0/3.0,.12)+band(a,b,7.0/3.0,3.0,.085)
        +band(a,b,3.0,11.0/3.0,.065)+band(a,b,11.0/3.0,13.0/3.0,.045)+band(a,b,13.0/3.0,5.0,.035))/max(b-a,.0001);
}
float around(float turn,float radius) {
    float2 h=box.zw;
    float t0=atan(max(h.x-min(1.53*radius,h.x),0.0),max(h.y,.0001));
    float t1=max(t0,atan(max(h.x-min(radius,h.x),0.0),max(h.y,.0001)));
    float t2=max(t1,atan(max(h.x-min(.3*radius,h.x),0.0),max(h.y-min(.3*radius,h.y),.0001)));
    float t3=max(t2,atan(h.x,max(h.y-min(radius,h.y),.0001)));
    float t4=max(t3,atan(h.x,max(h.y-min(1.5*radius,h.y),.0001)));
    float t5=max(t4,atan(h.x,max(h.y-min(2.0*radius,h.y),.0001)));
    return 1.0-.32*clamp(turn/max(t0,.00001),0.0,1.0)-.20*clamp((turn-t0)/max(t1-t0,.00001),0.0,1.0)
       -.10*clamp((turn-t1)/max(t2-t1,.00001),0.0,1.0)-.21*clamp((turn-t2)/max(t3-t2,.00001),0.0,1.0)
       -.10*clamp((turn-t3)/max(t4-t3,.00001),0.0,1.0)-.04*clamp((turn-t4)/max(t5-t4,.00001),0.0,1.0)
       -.03*clamp((turn-t5)/max(1.5707963-t5,.00001),0.0,1.0);
}
half4 main(float2 p) {
    float unit=geometry.z, radius=min(geometry.x,min(box.z,box.w));
    float2 centered=p-box.xy;
    float d=distanceBox(centered,box.zw,radius), edge=insetCurve(centered);
    float2 normal=normalBox(centered,box.zw,radius);
    if(geometry.y>0.0) {
        float other=distanceBox(p-drop.xy,drop.zw,min(drop.z,drop.w));
        float nearest=min(d,other), lost=max(-d-edge,0.0);
        if(other<d) {
            float2 q=abs(p-drop.xy), h=drop.zw;
            if(h.x<h.y) { q=q.yx; h=h.yx; }
            float c=h.x-h.y;
            float pill=q.y+c<q.x?h.y-length(q-float2(c,0)):octant(float2(q.x,q.y+c),h.x,pillCurve,pillCenter);
            lost=max(-other-pill,0.0);
        }
        float h=clamp(.5+.5*(other-d)/geometry.y,0.0,1.0);
        d=mix(other,d,h)-geometry.y*h*(1.0-h);
        normal=mix(normalBox(p-drop.xy,drop.zw,min(drop.z,drop.w)),normal,h);
        normal=normal/max(length(normal),.0001);
        edge=max(-d-max(lost-(nearest-d),0.0),0.0);
    }
    float inset=max(-d,0.0), minor=min(box.z,box.w);
    float bevel=max(min(20.0*unit,minor*.5),.001);
    float rise=1.0-clamp(inset/bevel,0.0,1.0);
    float depth=min(refraction*amount*unit,minor)*(1.0-sqrt(max(1.0-rise*rise,0.0)));
    half4 base=mix(sampleRaw(p-normal*depth),sampleFrost(p-normal*depth),amount);
    float3 color=base.rgb/max(float(base.a),.0001);
    color=mix(float3(dot(color,luma)),color,1.0+(mix(2.0,1.75,geometry.w)-1.0)*amount)*(1.0+(mix(1.1,1.8,geometry.w)-1.0)*amount);
    float contrast=-.53*geometry.w*amount;
    half4 behind=(sampleFrost(box.xy)+sampleFrost(box.xy+box.zw*float2(-.6,-.4))+sampleFrost(box.xy+box.zw*float2(.6,-.4))
        +sampleFrost(box.xy+box.zw*float2(-.6,.4))+sampleFrost(box.xy+box.zw*float2(.6,.4)))*.2;
    float clash=clamp((sign(contrast)*(dot(tint.rgb,luma)-dot(behind.rgb/max(float(behind.a),.0001),luma))-.3)/.5,0.0,1.0);
    float cover=tint.a*amount+(1.0-tint.a*amount)*clash*abs(contrast);
    float alpha=base.a*(1.0-cover)+cover;
    float3 glass=color*base.a*(1.0-cover)+tint.rgb*cover;
    float across=min(radius/max(box.z*2.0,.001),.4), x=abs(centered.x)/(box.z*2.0)+.5;
    x=min(x,1.0-x);
    float side=mix(.25,.27,geometry.w), corner=mix(.15,.1,geometry.w), flatTone=mix(.09,.02,geometry.w);
    float darken=mix(side,corner,clamp(x/max(across/4.0,.0001),0.0,1.0));
    darken=mix(darken,flatTone,clamp((x-across/4.0)/max(across*.75,.0001),0.0,1.0));
    half4 under=sampleRaw(p);
    float line=clamp((.67*unit-edge)+.5,0.0,1.0);
    glass=mix(glass,under.rgb*(1.0-darken),line*amount);
    glass+=alpha*around(atan(abs(centered.x),max(abs(centered.y),.0001)),radius)*shine(max(edge,0.0),unit)*mix(.16,.22,geometry.w)*amount;
    return half4(min(glass,float3(alpha)),alpha);
}
"""
