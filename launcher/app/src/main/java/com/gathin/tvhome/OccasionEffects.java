package com.gathin.tvhome;

import android.graphics.*;

/** Cached festive artwork. All paths, paints and gradients are allocated once, not per frame. */
final class OccasionEffects {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path veins = new Path();
    private final Shader fire = new LinearGradient(0,-1.6f,0,1,new int[]{0xFFFFF0A0,0xFFFFD65B,0xFFFF9B25,0xFFEB6630},new float[]{0,.38f,.75f,1},Shader.TileMode.CLAMP);
    private final Shader brass = new LinearGradient(-1,0,1,0,new int[]{0xFFA56B2B,0xFFF0C46A,0xFFC08C3E,0xFF765125},new float[]{0,.35f,.65f,1},Shader.TileMode.CLAMP);
    private final Path petal = new Path(), leaf = new Path(), bell = new Path(), flame = new Path(), star = new Path(), blossom = new Path(), wave = new Path();
    private final RadialGradient gold = gradient(0xFFFFC46A);
    private final RadialGradient[] clouds = {gradient(0xFFED358B),gradient(0xFF22C6CF),gradient(0xFFFF841F),gradient(0xFFFFD736)};
    private final int[] marigold = {0xFFFFB920,0xFFFF791E,0xFFBA2843};
    private final int[] powderColors = {0xFFED358B,0xFF22C6CF,0xFFFF841F,0xFFFFD736};
    private PeacockFeather feather;
    OccasionEffects() {
        stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeCap(Paint.Cap.ROUND);
        glyph.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL)); glyph.setTextAlign(Paint.Align.CENTER);
        petal.moveTo(-1,0); petal.cubicTo(-.35f,-1,.7f,-.85f,1,0); petal.cubicTo(.35f,1,-.7f,.85f,-1,0); petal.close();
        leaf.moveTo(0,1.5f); leaf.cubicTo(-.15f,.65f,-1.2f,.4f,-.8f,-.4f); leaf.cubicTo(-.45f,-1.1f,0,-.65f,0,-.45f);
        leaf.cubicTo(0,-.65f,.45f,-1.1f,.8f,-.4f); leaf.cubicTo(1.2f,.4f,.15f,.65f,0,1.5f); leaf.close();
        bell.moveTo(-1,.5f); bell.quadTo(-.65f,.2f,-.6f,-.5f); bell.quadTo(-.55f,-1.2f,0,-1.2f);
        bell.quadTo(.55f,-1.2f,.6f,-.5f); bell.quadTo(.65f,.2f,1,.5f); bell.quadTo(0,.9f,-1,.5f); bell.close();
        flame.moveTo(0,-1.6f); flame.cubicTo(-.1f,-.5f,-1,-.2f,-.6f,.5f); flame.cubicTo(-.3f,1.2f,.7f,1,.7f,.2f); flame.cubicTo(.7f,-.45f,.1f,-.65f,0,-1.6f); flame.close();
        star.moveTo(0,-1); star.quadTo(.1f,-.1f,1,0); star.quadTo(.1f,.1f,0,1); star.quadTo(-.1f,.1f,-1,0); star.quadTo(-.1f,-.1f,0,-1); star.close();
        Path p = new Path(); p.moveTo(0,0); p.cubicTo(-.6f,-.3f,-.5f,-1.1f,0,-1); p.cubicTo(.5f,-1.1f,.6f,-.3f,0,0); p.close();
        Matrix m = new Matrix(); for(int i=0;i<12;i++) { m.setRotate(i*30); blossom.addPath(p,m); }
        veins.moveTo(0,-.45f); veins.lineTo(0,1.3f);
        for(int i=0;i<4;i++) {
            float y=-.2f+i*.24f,span=.55f-i*.1f;
            veins.moveTo(0,y+.22f);veins.quadTo(-span*.6f,y,-span,y-.15f);
            veins.moveTo(0,y+.22f);veins.quadTo(span*.6f,y,span,y-.15f);
        }
    }
    private static RadialGradient gradient(int color) {
        return new RadialGradient(0,0,1,new int[]{(color&0xFFFFFF)|0xBB000000,(color&0xFFFFFF)|0x55000000,color&0xFFFFFF},new float[]{0,.38f,1},Shader.TileMode.CLAMP);
    }
    private void halo(Canvas c,float x,float y,float rx,float ry,Shader shader,float alpha) {
        c.save(); c.translate(x,y); c.scale(rx,ry); glow.setShader(shader); glow.setAlpha((int)(alpha*255)); c.drawCircle(0,0,1,glow); c.restore();
    }
    private void color(int color,float alpha) { fill.setColor(color); fill.setAlpha((int)(Math.max(0,Math.min(1,alpha))*255)); }
    boolean draw(Canvas c,String effect,float t,int w,int h,boolean animated,float safeBottom) {
        switch(effect) {
            case "diya": case "gulal": case "temple": case "marigold": case "mantra": case "starlight": case "cosmic": case "peacock": case "leaves": break;
            default: return false;
        }
        float u=w/960f, pulse=.5f+.5f*(float)Math.sin(t*.6);
        if(effect.equals("gulal")) {
            for(int i=0;i<5;i++) halo(c,w*(.1f+i*.21f+(float)Math.sin(t*.08+i)*.12f),h*(.3f+(float)Math.sin(t*.12+i*1.8)*.3f),w*.37f,h*(.32f+i%2*.16f),clouds[i%4],.52f+.08f*(float)Math.sin(t*.18+i));
            for(int i=0;i<24;i++) {
                color(powderColors[i%4],.12f+.16f*(.5f+.5f*(float)Math.sin(t*.8+i)));
                c.drawCircle(w*((i*.618f+(float)Math.sin(t*.1+i)*.06f+2)%1),h*((i*.173f+t*.005f)%1),(.5f+i%3*.4f)*u,fill);
            }
            return true;
        }
        if(effect.equals("temple")||effect.equals("mantra")) {
            halo(c,w*.5f,h*.45f,w*(.33f+pulse*.05f),h*.65f,gold,.25f+pulse*.16f);
            if(effect.equals("temple")) for(int side=0;side<2;side++) {
                float x=side==0?.08f:.92f;
                c.save(); c.translate(w*x,0); c.rotate((float)Math.toDegrees(Math.sin(t*.8+x)*.08));
                stroke.setColor(0xE6D9A753); stroke.setStrokeWidth(2*u); c.drawLine(0,0,0,52*u,stroke);
                c.translate(0,84*u); c.scale(30*u,30*u); color(Color.WHITE,.9f); fill.setShader(brass); c.drawPath(bell,fill); fill.setShader(null);
                stroke.setColor(0xE6FFE0A0); stroke.setStrokeWidth(.045f); c.drawPath(bell,stroke);
                stroke.setAlpha(128);stroke.setStrokeWidth(.025f);wave.rewind();wave.moveTo(-.52f,-.75f);wave.quadTo(0,-.52f,.52f,-.75f);wave.moveTo(-.62f,-.22f);wave.quadTo(0,.02f,.62f,-.22f);c.drawPath(wave,stroke);
                color(0xFFFFE1A1,.85f);for(int n=0;n<5;n++)c.drawCircle(-.5f+n*.25f,.32f,.035f,fill);
                color(0xFFFFE1A1,.9f); c.drawOval(-.85f,.41f,.85f,.69f,fill); c.drawCircle((float)Math.sin(t*.8)*.12f,.65f,.12f,fill); c.restore();
            }
            if(effect.equals("mantra")) {
                glyph.setColor(0xFFFFE1A0); glyph.setAlpha((int)(255*(.34f+pulse*.3f))); glyph.setTextSize(72*u); c.drawText("ॐ",w*.5f,h*.58f,glyph);
                c.save();c.translate(w*.5f,h*.5f);c.rotate((float)Math.toDegrees(t*.025));stroke.setColor(0xFFEFCE8C);stroke.setAlpha((int)(255*(.12f+pulse*.06f)));stroke.setStrokeWidth(u);
                for(int ring=0;ring<2;ring++){float rx=(68+ring*14)*u,ry=(60+ring*12)*u;c.drawOval(-rx,-ry,rx,ry,stroke);}c.restore();
            }
        }
        if(effect.equals("diya")) for(int side=0;side<2;side++) {
            float x=side==0?.035f:.965f;
            float floor=safeBottom-24*u,phase=t+x*5;
            halo(c,w*x,floor-30*u,90*u,100*u,gold,.42f+.05f*(float)Math.sin(phase*7));
            c.save(); c.translate(w*x,floor); c.scale(26*u,24*u);
            color(0xFFA04D2A,1); c.drawArc(-1.2f,-.55f,1.2f,.55f,0,180,false,fill);
            stroke.setColor(0xFFEDAA58); stroke.setStrokeWidth(.08f); c.drawOval(-1.2f,-.17f,1.2f,.17f,stroke);
            color(0xFFC77C38,.8f);c.drawOval(-1.1f,-.12f,1.1f,.12f,fill);stroke.setColor(0xFFF3BD68);stroke.setStrokeWidth(.035f);
            for(int n=0;n<7;n++){float px=-.84f+n*.28f;wave.rewind();wave.moveTo(px,.2f);wave.quadTo(px+.1f,.46f,px+.22f,.17f);c.drawPath(wave,stroke);}
            stroke.setColor(0xFF4E3023);stroke.setStrokeWidth(.1f);c.drawLine(-.05f,-.05f,.05f,-.38f,stroke);
            c.translate(0,-.78f);c.rotate((float)Math.toDegrees(Math.sin(phase*4.2)*.045)); c.scale(.6f+(float)Math.sin(phase*8)*.04f,.75f+(float)Math.sin(phase*6)*.06f);
            color(Color.WHITE,1);fill.setShader(fire); c.drawPath(flame,fill);fill.setShader(null); c.scale(.45f,.7f); color(0xFFFFF1B2,1); c.drawPath(flame,fill); c.restore();
        }
        if(effect.equals("cosmic")) {
            for(int row=0;row<5;row++) {
                wave.rewind();
                for(int x=0;x<=40;x++) {
                    float y=safeBottom-(50-row*9)*u+(float)Math.sin(x*.38+t*.65+row)*(3+row)*u;
                    if(x==0)wave.moveTo(0,y); else wave.lineTo(w*x/40f,y);
                }
                stroke.setColor(row%2==0?0xFF62B9C1:0xFFC3E4DE); stroke.setAlpha((int)(255*(.16f+row*.025f))); stroke.setStrokeWidth(u*1.4f); c.drawPath(wave,stroke);
            }
        }
        if(effect.equals("peacock")) {
            if(feather==null)feather=new PeacockFeather();
            for(int side=-1;side<=1;side+=2) {
                c.save();c.translate(w*(side<0?.06f:.94f),h*.7f);
                c.rotate((float)Math.toDegrees(side*(.27+Math.sin(t*.28)*.07)));c.scale(48*u,85*u);
                feather.draw(c,t,side);c.restore();
            }
        }
        boolean rising=effect.equals("diya")||effect.equals("starlight")||effect.equals("cosmic")||effect.equals("peacock");
        int count=!animated?10:effect.equals("marigold")?(t<10?70:46):effect.equals("leaves")?42:effect.equals("diya")?32:effect.equals("peacock")?18:48;
        for(int i=0;i<count;i++) {
            float seed=i*.6180339887f%1, depth=.4f+i%3*.3f;
            float x=((seed+(float)Math.sin(t*.22+i)*.06f+2)%1)*w;
            float phase=(i*.173205f+t*(.008f+depth*.02f))%1,y=(rising?1-phase:phase)*h;
            if(effect.equals("diya")){x=w*(i%2!=0?.965f:.035f)+(float)Math.sin(i+t*.4)*phase*32*u;y=safeBottom-50*u-phase*h*.35f;}
            float radius=(effect.equals("marigold")||effect.equals("leaves")?10+seed*10:effect.equals("diya")?1.2f+seed*2.5f:effect.equals("peacock")?1+seed*2:2+seed*4)*u*depth;
            float sine=(float)Math.sin(t*1.1+i),alpha=(.35f+depth*.4f)*(.55f+.45f*sine*sine);
            if(effect.equals("diya"))alpha*=1-phase;
            c.save(); c.translate(x,y); c.rotate((float)Math.toDegrees(t*.2+i)); c.scale(radius,radius);
            if(effect.equals("marigold")) {
                c.scale(.5f+.5f*Math.abs((float)Math.cos(t*.7+i)),1);
                color(marigold[i%3],alpha); c.drawPath(i%3==2?petal:blossom,fill);
                if(i%3!=2) { c.scale(.6f,.6f); c.rotate(14.3f); color(0xFFFFDB64,alpha); c.drawPath(blossom,fill);c.rotate(11.5f);c.scale(.55f,.55f);color(0xFFF39124,alpha);c.drawPath(blossom,fill); }
                else {stroke.setColor(0xFFFF9DA1);stroke.setAlpha((int)(255*alpha));stroke.setStrokeWidth(.07f);wave.rewind();wave.moveTo(-.7f,0);wave.quadTo(0,-.18f,.65f,0);c.drawPath(wave,stroke);}
            } else if(effect.equals("leaves")) {
                c.scale(.5f+.5f*Math.abs((float)Math.cos(t*.5+i)),1); color(0xFFDFBC68,alpha); c.drawPath(leaf,fill);
                stroke.setColor(0xFFFFF2B6); stroke.setAlpha((int)(255*alpha*.65f)); stroke.setStrokeWidth(.06f); c.drawPath(veins,stroke);
            } else {
                color(effect.equals("peacock")?(i%2!=0?0xFF8CC9B4:0xFFB1BB95):i%3!=0?0xFFFFD68A:0xFFFFF4DC,alpha);
                if(effect.equals("diya")&&i%8!=0)c.drawCircle(0,0,.7f,fill);else c.drawPath(star,fill);
                if(effect.equals("starlight")&&i%3==0) { stroke.setColor(0xFFFFE4A6); stroke.setAlpha((int)(255*alpha)); stroke.setStrokeWidth(.12f); c.drawLine(0,1,0,4,stroke); }
            }
            c.restore();
        }
        return true;
    }
}
