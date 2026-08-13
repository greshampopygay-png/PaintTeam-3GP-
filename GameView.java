package com.paintteam3d;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import java.util.*;

public class GameView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private float playerX=0, playerY=0, aimX=1, aimY=0;
    private float joyX=0, joyY=0;
    private boolean firing=false;
    private int hp=100, score=0;
    private long last=System.nanoTime();
    private final ArrayList<Dot> paintShots = new ArrayList<>();
    private final ArrayList<Bot> bots = new ArrayList<>();
    private float redTank=1000, blueTank=1000;
    private int team=0; // 0 red, 1 blue
    private long respawnAt=0;
    private String status="КРАСНАЯ КОМАНДА";

    private static class Dot {
        float x,y,vx,vy;
        int color;
        Dot(float x,float y,float vx,float vy,int c){this.x=x;this.y=y;this.vx=vx;this.vy=vy;this.color=c;}
    }
    private static class Bot {
        float x,y,tx,ty;
        int hp=100, team;
        Bot(float x,float y,int t){this.x=x;this.y=y;this.team=t; this.tx=x;this.ty=y;}
    }

    public GameView(Context c){
        super(c);
        setFocusable(true);
        for(int i=0;i<10;i++){
            int t=i%2;
            bots.add(new Bot((t==0?-1:1)*(220+rnd.nextInt(300)),
                    -450+rnd.nextInt(900),t));
        }
    }

    private float sx(float worldX){ return getWidth()/2f + (worldX-playerX)*0.75f; }
    private float sy(float worldY){ return getHeight()/2f + (worldY-playerY)*0.75f; }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        long now=System.nanoTime();
        float dt=Math.min(0.04f,(now-last)/1e9f);
        last=now;
        update(dt);

        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(22,25,31)); c.drawRect(0,0,getWidth(),getHeight(),p);

        drawArena(c);
        drawTanks(c);
        for(Bot b:bots) drawBot(c,b);
        drawPlayer(c);
        drawShots(c);
        drawHud(c);
        drawControls(c);

        postInvalidateDelayed(16);
    }

    private void update(float dt){
        if(respawnAt>0){
            if(System.currentTimeMillis()>=respawnAt){ hp=100; playerX=team==0?-500:500; playerY=0; respawnAt=0; }
            return;
        }
        float speed=330;
        playerX += joyX*speed*dt;
        playerY += joyY*speed*dt;
        playerX=Math.max(-1250,Math.min(1250,playerX));
        playerY=Math.max(-750,Math.min(750,playerY));

        for(Bot b:bots){
            float dx=playerX-b.x, dy=playerY-b.y;
            float d=(float)Math.hypot(dx,dy);
            if(d<700){
                b.tx=playerX; b.ty=playerY;
            } else if(Math.hypot(b.tx-b.x,b.ty-b.y)<30){
                b.tx=(b.team==0?-1:1)*(250+rnd.nextInt(700));
                b.ty=-600+rnd.nextInt(1200);
            }
            float bx=b.tx-b.x, by=b.ty-b.y, bd=(float)Math.hypot(bx,by);
            if(bd>5){b.x+=bx/bd*130*dt;b.y+=by/bd*130*dt;}
            if(d<430 && rnd.nextFloat()<dt*0.7f){
                float nx=dx/Math.max(1,d), ny=dy/Math.max(1,d);
                paintShots.add(new Dot(b.x,b.y,nx*650,ny*650,b.team==0?Color.RED:Color.BLUE));
            }
        }

        if(firing){
            float len=(float)Math.hypot(aimX,aimY);
            if(len<0.1f){aimX=1;aimY=0;} else {aimX/=len;aimY/=len;}
            // limited rate by adding a marker shot; the visible blob also deals damage.
            if(rnd.nextFloat()<dt*9){
                paintShots.add(new Dot(playerX+aimX*40,playerY+aimY*40,aimX*850,aimY*850,Color.RED));
            }
        }

        Iterator<Dot> it=paintShots.iterator();
        while(it.hasNext()){
            Dot s=it.next();
            s.x+=s.vx*dt;s.y+=s.vy*dt;
            boolean hit=false;
            for(Bot b:bots){
                if(b.team==team) continue;
                if(Math.hypot(s.x-b.x,s.y-b.y)<38){
                    b.hp-=25; hit=true;
                    if(b.hp<=0){b.hp=100;b.x=b.team==0?-800:800;b.y=rnd.nextInt(1000)-500;score++;}
                    break;
                }
            }
            // Player hit by blue paint
            if(s.color==Color.BLUE && Math.hypot(s.x-playerX,s.y-playerY)<38){
                hp-=20; hit=true;
                if(hp<=0){respawnAt=System.currentTimeMillis()+900;playerX=team==0?-500:500;playerY=0;}
            }
            // Tanks
            if(Math.hypot(s.x-(team==0?950:-950),s.y)<150 && s.color!=Color.RED){
                if(team==0) redTank-=3; else blueTank-=3; hit=true;
            }
            if(Math.hypot(s.x-(team==0?-950:950),s.y)<150 && s.color==Color.RED){
                if(team==0) blueTank-=3; else redTank-=3; hit=true;
            }
            if(Math.abs(s.x)>1400 || Math.abs(s.y)>900 || hit) it.remove();
        }
    }

    private void drawArena(Canvas c){
        // grid
        p.setColor(Color.rgb(40,45,52)); p.setStrokeWidth(2);
        for(int x=-1400;x<=1400;x+=100)c.drawLine(sx(x),sy(-850),sx(x),sy(850),p);
        for(int y=-850;y<=850;y+=100)c.drawLine(sx(-1400),sy(y),sx(1400),sy(y),p);
        // center zone
        p.setColor(Color.argb(80,255,255,255));
        c.drawCircle(sx(0),sy(0),210,p);
        // bases
        p.setColor(Color.argb(90,255,0,0));c.drawCircle(sx(-950),sy(0),260,p);
        p.setColor(Color.argb(90,0,80,255));c.drawCircle(sx(950),sy(0),260,p);
    }

    private void drawTanks(Canvas c){
        drawTank(c,-950,0,Color.RED,redTank,"КРАСНАЯ");
        drawTank(c,950,0,Color.BLUE,blueTank,"СИНЯЯ");
    }

    private void drawTank(Canvas c,float x,float y,int col,float hpTank,String name){
        float X=sx(x),Y=sy(y);
        p.setColor(Color.DKGRAY);c.drawRoundRect(X-95,Y-65,X+95,Y+65,25,25,p);
        p.setColor(col);c.drawRoundRect(X-75,Y-48,X+75,Y+48,20,20,p);
        p.setColor(Color.LTGRAY);c.drawCircle(X,Y,45,p);
        p.setColor(Color.DKGRAY);c.drawRect(X-150,Y-85,X+150,Y-70,p);
        p.setColor(Color.GREEN);c.drawRect(X-150,Y-85,X-150+300*Math.max(0,hpTank/1000f),Y-70,p);
        p.setTextSize(24);p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.WHITE);
        c.drawText(name+" • "+Math.max(0,(int)hpTank)+" HP",X,Y+115,p);
    }

    private void drawBot(Canvas c,Bot b){
        float X=sx(b.x),Y=sy(b.y);
        p.setColor(b.team==0?Color.rgb(220,40,40):Color.rgb(40,100,240));
        c.drawCircle(X,Y,32,p);
        p.setColor(Color.DKGRAY);c.drawRect(X-22,Y+25,X+22,Y+75,p);
        p.setColor(Color.WHITE);c.drawCircle(X-10,Y-5,5,p);c.drawCircle(X+10,Y-5,5,p);
        p.setColor(Color.BLACK);c.drawCircle(X-10,Y-5,2,p);c.drawCircle(X+10,Y-5,2,p);
        p.setColor(Color.BLACK);c.drawRect(X-30,Y-48,X+30,Y-42,p);
        p.setColor(Color.GREEN);c.drawRect(X-30,Y-48,X-30+60*b.hp/100f,Y-42,p);
    }

    private void drawPlayer(Canvas c){
        float X=sx(playerX),Y=sy(playerY);
        p.setColor(Color.RED);c.drawCircle(X,Y,38,p);
        p.setColor(Color.WHITE);c.drawCircle(X-12,Y-7,6,p);c.drawCircle(X+12,Y-7,6,p);
        p.setColor(Color.BLACK);c.drawCircle(X-12,Y-7,3,p);c.drawCircle(X+12,Y-7,3,p);
        p.setColor(Color.DKGRAY);c.drawRect(X-28,Y+30,X+28,Y+85,p);
        p.setStrokeWidth(14);p.setColor(Color.RED);c.drawLine(X,Y,X+aimX*80,Y+aimY*80,p);
    }

    private void drawShots(Canvas c){
        for(Dot s:paintShots){
            p.setColor(s.color);c.drawCircle(sx(s.x),sy(s.y),11,p);
        }
    }

    private void drawHud(Canvas c){
        p.setColor(Color.argb(185,0,0,0));c.drawRoundRect(18,18,360,110,20,20,p);
        p.setTextAlign(Paint.Align.LEFT);p.setTextSize(24);p.setColor(Color.WHITE);
        c.drawText(status,35,50,p);
        p.setColor(Color.GREEN);c.drawRect(35,68,325,88,p);
        p.setColor(Color.RED);c.drawRect(35,68,35+290*hp/100f,88,p);
        p.setColor(Color.WHITE);c.drawText("HP "+hp+"   Очки "+score,35,105,p);

        if(respawnAt>0){
            p.setTextAlign(Paint.Align.CENTER);p.setTextSize(42);p.setColor(Color.WHITE);
            c.drawText("ВОЗРОЖДЕНИЕ...",getWidth()/2f,getHeight()/2f,p);
        }
        if(redTank<=0 || blueTank<=0){
            p.setTextSize(48);p.setColor(Color.YELLOW);
            c.drawText(redTank<=0?"СИНЯЯ КОМАНДА ПОБЕДИЛА!":"КРАСНАЯ КОМАНДА ПОБЕДИЛА!",
                    getWidth()/2f,150,p);
        }
    }

    private void drawControls(Canvas c){
        float baseX=145,baseY=getHeight()-145;
        p.setColor(Color.argb(90,255,255,255));c.drawCircle(baseX,baseY,105,p);
        p.setColor(Color.argb(160,255,255,255));c.drawCircle(baseX+joyX*65,baseY+joyY*65,42,p);

        float fireX=getWidth()-145,fireY=getHeight()-145;
        p.setColor(firing?Color.argb(220,255,120,70):Color.argb(120,255,255,255));
        c.drawCircle(fireX,fireY,95,p);
        p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(26);
        c.drawText("КРАСКА",fireX,fireY+9,p);

        p.setTextSize(18);p.setColor(Color.WHITE);
        c.drawText("Двигайся",baseX,baseY+140,p);
        c.drawText("СТРЕЛЬБА",fireX,fireY+125,p);
    }

    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        float x=e.getX(),y=e.getY();
        float baseX=145,baseY=getHeight()-145, fireX=getWidth()-145,fireY=getHeight()-145;
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                if(Math.hypot(x-baseX,y-baseY)<150){
                    joyX=(x-baseX)/100f;joyY=(y-baseY)/100f;
                    float l=(float)Math.hypot(joyX,joyY);if(l>1){joyX/=l;joyY/=l;}
                }
                if(Math.hypot(x-fireX,y-fireY)<120){
                    firing=true;
                } else if(Math.hypot(x-baseX,y-baseY)>=150 && x<getWidth()/2f){
                    aimX=x-getWidth()/2f;aimY=y-getHeight()/2f;
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if(Math.hypot(x-baseX,y-baseY)<170){joyX=0;joyY=0;}
                firing=false;
                return true;
        }
        return true;
    }
}
