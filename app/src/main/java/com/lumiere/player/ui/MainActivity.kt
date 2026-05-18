package com.lumiere.player.enhancement

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

class LumiereVideoEffect(private val params: EnhanceParams) : GlEffect {
    val shader = LumiereShaderProgram(params)
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = shader
}

class LumiereShaderProgram(private val params: EnhanceParams) : GlShaderProgram {

    var faceRegions: Array<FloatArray> = emptyArray()
    var sceneType: Int = 0 // 0=normal,1=outdoor,2=indoor,3=night

    private var glProgram: GlProgram? = null
    private var texW = 1920; private var texH = 1080

    private val VERT = """
        attribute vec4 aPosition;
        attribute vec4 aTexCoords;
        varying vec2 vUV;
        void main() { gl_Position = aPosition; vUV = aTexCoords.xy; }
    """.trimIndent()

    private val FRAG = """
        precision highp float;
        uniform sampler2D uTex;
        varying vec2 vUV;
        uniform float uBright, uContrast, uSat, uWarmth, uShadow, uSharp, uNoise;
        uniform vec2  uTS;
        uniform int   uHDR, uDeint, uScene, uFaceN;
        uniform vec4  uF0, uF1, uF2, uF3;
        uniform float uFaceBoost;

        float luma(vec3 c){ return dot(c, vec3(0.2126,0.7152,0.0722)); }

        vec3 bilateral(vec2 uv, vec3 ctr){
            vec3 s=vec3(0.); float w=0.;
            for(int x=-1;x<=1;x++) for(int y=-1;y<=1;y++){
                vec2 o=vec2(float(x),float(y))*uTS;
                vec3 n=texture2D(uTex,uv+o).rgb;
                float wt=exp(-dot(n-ctr,n-ctr)*8.);
                s+=n*wt; w+=wt;
            }
            return s/w;
        }

        vec3 usm(vec2 uv, vec3 c){
            vec3 b=
              texture2D(uTex,uv+vec2(-uTS.x,-uTS.y)).rgb*0.0625+
              texture2D(uTex,uv+vec2(0.,-uTS.y)).rgb*0.125+
              texture2D(uTex,uv+vec2(uTS.x,-uTS.y)).rgb*0.0625+
              texture2D(uTex,uv+vec2(-uTS.x,0.)).rgb*0.125+
              c*0.25+
              texture2D(uTex,uv+vec2(uTS.x,0.)).rgb*0.125+
              texture2D(uTex,uv+vec2(-uTS.x,uTS.y)).rgb*0.0625+
              texture2D(uTex,uv+vec2(0.,uTS.y)).rgb*0.125+
              texture2D(uTex,uv+vec2(uTS.x,uTS.y)).rgb*0.0625;
            return c+(c-b)*uSharp*2.;
        }

        vec3 hdr(vec3 c){
            c*=1.2;
            return c*(1.+c/4.84)/(1.+c);
        }

        vec3 deint(vec2 uv, vec3 c){
            float line=floor(uv.y/uTS.y);
            if(mod(line,2.)<1.){
                vec3 a=texture2D(uTex,uv-vec2(0.,uTS.y)).rgb;
                vec3 b=texture2D(uTex,uv+vec2(0.,uTS.y)).rgb;
                return (a+b)*.5;
            }
            return c;
        }

        float faceW(vec2 uv){
            float w=0.;
            if(uFaceN>0){ vec2 ct=(uF0.xy+uF0.zw)*.5; float r=length(uF0.zw-uF0.xy)*.5; w=max(w,clamp(1.-length(uv-ct)/max(r,.001),0.,1.)); }
            if(uFaceN>1){ vec2 ct=(uF1.xy+uF1.zw)*.5; float r=length(uF1.zw-uF1.xy)*.5; w=max(w,clamp(1.-length(uv-ct)/max(r,.001),0.,1.)); }
            if(uFaceN>2){ vec2 ct=(uF2.xy+uF2.zw)*.5; float r=length(uF2.zw-uF2.xy)*.5; w=max(w,clamp(1.-length(uv-ct)/max(r,.001),0.,1.)); }
            if(uFaceN>3){ vec2 ct=(uF3.xy+uF3.zw)*.5; float r=length(uF3.zw-uF3.xy)*.5; w=max(w,clamp(1.-length(uv-ct)/max(r,.001),0.,1.)); }
            return w;
        }

        void main(){
            vec4 raw=texture2D(uTex,vUV);
            vec3 c=raw.rgb;
            if(uDeint==1) c=deint(vUV,c);
            if(uNoise>0.) c=mix(c,bilateral(vUV,c),uNoise*.7);
            c=c+uShadow*(1.-c);
            c*=uBright;
            c=(c-.5)*uContrast+.5;
            c.r=clamp(c.r+uWarmth*.07,0.,1.);
            c.g=clamp(c.g+uWarmth*.016,0.,1.);
            c.b=clamp(c.b-uWarmth*.047,0.,1.);
            float l=luma(c);
            c=mix(vec3(l),c,uSat);
            if(uSharp>0.) c=usm(vUV,c);
            if(uScene==1){ c.g=clamp(c.g*1.04,0.,1.); c.b=clamp(c.b*1.03,0.,1.); }
            else if(uScene==3){ c=mix(c,vec3(luma(c)),.05); c+=.02; }
            float fw=faceW(vUV);
            if(fw>0.&&uFaceBoost>0.){
                c=mix(c,usm(vUV,c),fw*uFaceBoost*.5);
                c.r=clamp(c.r+.02*fw*uFaceBoost,0.,1.);
                c.g=clamp(c.g+.01*fw*uFaceBoost,0.,1.);
            }
            if(uHDR==1) c=hdr(c);
            gl_FragColor=vec4(clamp(c,0.,1.),raw.a);
        }
    """.trimIndent()

    override fun configure(w: Int, h: Int): androidx.media3.common.util.Size {
        texW = w; texH = h
        glProgram = GlProgram(VERT, FRAG)
        return androidx.media3.common.util.Size(w, h)
    }

    override fun drawFrame(texId: Int, pts: Long) {
        val p = glProgram ?: return
        try {
            p.use()
            p.setSamplerTexIdUniform("uTex", texId, 0)
            if (params.enabled) {
                p.setFloatUniform("uBright",  params.brightness)
                p.setFloatUniform("uContrast",params.contrast)
                p.setFloatUniform("uSat",     params.saturation)
                p.setFloatUniform("uWarmth",  params.warmth)
                p.setFloatUniform("uShadow",  params.shadow)
                p.setFloatUniform("uSharp",   params.sharpness)
                p.setFloatUniform("uNoise",   params.noise)
            } else {
                listOf("uBright","uContrast","uSat").forEach { p.setFloatUniform(it, 1f) }
                listOf("uWarmth","uShadow","uSharp","uNoise").forEach { p.setFloatUniform(it, 0f) }
            }
            p.setFloatsUniform("uTS", floatArrayOf(1f/texW, 1f/texH))
            p.setIntUniform("uHDR",   if (params.hdrSim) 1 else 0)
            p.setIntUniform("uDeint", if (params.deinterlace) 1 else 0)
            p.setIntUniform("uScene", sceneType)
            val fc = if (params.faceEnhance) minOf(faceRegions.size, 4) else 0
            p.setIntUniform("uFaceN", fc)
            p.setFloatUniform("uFaceBoost", if (params.faceEnhance) 1f else 0f)
            val empty = floatArrayOf(0f,0f,0f,0f)
            p.setFloatsUniform("uF0", if (fc>0) faceRegions[0] else empty)
            p.setFloatsUniform("uF1", if (fc>1) faceRegions[1] else empty)
            p.setFloatsUniform("uF2", if (fc>2) faceRegions[2] else empty)
            p.setFloatsUniform("uF3", if (fc>3) faceRegions[3] else empty)
            p.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    override fun release() { glProgram?.delete(); glProgram = null }
}