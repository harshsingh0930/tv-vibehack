package com.gathin.tvhome;

import android.graphics.*;

/** Detailed strands are rasterised once; drawing a feather requires one small cached texture. */
final class PeacockFeather {
    private final Bitmap texture = Bitmap.createBitmap(256,416,Bitmap.Config.ARGB_8888);
    private final Paint image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF(-1.28f,-2.24f,1.28f,1.92f);
    private final Path[] highlights = {new Path(),new Path(),new Path()};
    PeacockFeather() {
        Canvas c=new Canvas(texture); c.translate(128,224);c.scale(100,100);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStrokeCap(Paint.Cap.ROUND);p.setStyle(Paint.Style.STROKE);
        int[] colors={0xFF419E80,0xFF72B365,0xFF22A8A1,0xFF3F9673,0xFFC4AC57,0xFF287C9E};
        Path path=new Path();
        for(int i=0;i<104;i++) {
            float y=1.28f-i*.0317f;
            float envelope=(float)Math.pow(Math.max(0,Math.sin(Math.PI*(y+2.05)/3.35)),.65);
            float gaussian=(y+.85f)/1.05f;
            float span=envelope*(.28f+.64f*(float)Math.exp(-gaussian*gaussian));
            for(int side=-1;side<=1;side+=2) {
                float length=span*(1+.055f*(float)Math.sin(i*2.17+side)),shaft=.025f*(float)Math.sin(y*1.5),tip=side*length;
                path.rewind();path.moveTo(shaft,y+.15f);path.cubicTo(side*length*.24f,y+.07f,tip*.78f,y-.17f,tip,y-.31f-.035f*(float)Math.sin(i));
                p.setColor(colors[(i+(side>0?2:0))%colors.length]);float sine=(float)Math.sin(i*1.31);p.setAlpha((int)(255*(.58f+.25f*sine*sine)));
                p.setStrokeWidth(i%3!=0?.006f:.009f);c.drawPath(path,p);
                if(i%4==0)highlights[i%3].addPath(path);
                if(i%2==0) {
                    p.setColor(0xFFD6C27E);p.setAlpha(77);p.setStrokeWidth(.0035f);
                    path.rewind();path.moveTo(tip*.66f,y-.09f);path.quadTo(tip*.87f,y-.18f,tip*1.065f,y-.31f);c.drawPath(path,p);
                }
            }
        }
        p.setShader(new LinearGradient(0,-2,0,1.8f,new int[]{0xFF75875B,0xFFC4B984,0xFFAD976B},new float[]{0,.6f,1},Shader.TileMode.CLAMP));
        p.setAlpha(209);p.setStrokeWidth(.014f);path.rewind();path.moveTo(-.025f,1.76f);path.cubicTo(.07f,.7f,-.07f,-.4f,-.01f,-1.99f);c.drawPath(path,p);p.setShader(null);
        Path eye=new Path();eye.moveTo(-.05f,-1);eye.cubicTo(-.63f,-.99f,-1.02f,-.27f,-.78f,.38f);eye.cubicTo(-.53f,1.02f,.28f,1.04f,.74f,.44f);eye.cubicTo(1.12f,-.05f,.61f,-1.04f,-.05f,-1);eye.close();
        c.save();c.translate(-.005f,-.99f);c.scale(.59f,.69f);p.setStyle(Paint.Style.FILL);
        float[] scales={1,.87f,.73f,.59f,.46f};
        int[] light={0xFFCFB566,0xFFE0CA79,0xFF65C797,0xFF32CBC2,0xFF457FDE},dark={0xFF997C39,0xFFA68E42,0xFF158372,0xFF168CAA,0xFF263F94};
        for(int i=0;i<scales.length;i++) {
            c.save();c.scale(scales[i],scales[i]);p.setShader(new RadialGradient(-.1f,-.15f,1.15f,new int[]{light[i],light[i],dark[i]},new float[]{0,.55f,1},Shader.TileMode.CLAMP));p.setAlpha(245);c.drawPath(eye,p);c.restore();
        }
        p.setShader(null);p.setColor(0xFF081A42);p.setAlpha(245);
        path.rewind();path.moveTo(.06f,-.37f);path.cubicTo(-.26f,-.48f,-.51f,-.18f,-.36f,.1f);path.cubicTo(-.27f,.27f,.02f,.35f,.19f,.13f);path.cubicTo(.35f,-.04f,.28f,-.25f,.06f,-.37f);path.close();c.drawPath(path,p);
        p.setStyle(Paint.Style.STROKE);p.setColor(0xFF8BDED5);p.setAlpha(69);p.setStrokeWidth(.012f);path.rewind();path.moveTo(-.27f,-.25f);path.quadTo(-.43f,-.07f,-.29f,.09f);c.drawPath(path,p);c.restore();
        sheen.setStyle(Paint.Style.STROKE);sheen.setStrokeCap(Paint.Cap.ROUND);sheen.setStrokeWidth(.008f);
    }
    void draw(Canvas c,float time,int side) {
        image.setAlpha(240);c.drawBitmap(texture,null,bounds,image);
        for(int i=0;i<highlights.length;i++) {
            sheen.setColor(i%2==0?0xFF78DECE:0xFFE2CE82);
            sheen.setAlpha((int)(38+35*Math.max(0,Math.sin(time*.55+side-i*.8))));
            c.drawPath(highlights[i],sheen);
        }
    }
}
