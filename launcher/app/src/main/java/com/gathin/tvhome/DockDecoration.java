package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Draws the tvOS style lighter band behind the first (dock) row and adds a gap below it. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final boolean mountain;
    private float shaderTop = Float.NaN, shaderBottom = Float.NaN;

    DockDecoration(int dockSize, float density, boolean mountain) {
        this.dockSize = dockSize;
        this.density = density;
        this.mountain = mountain;
        band.setColor(0xFF141416);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        edge.setColor(mountain ? 0x66FFFFFF : 0x1FFFFFFF);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            // Keep the next section below the resting Home viewport; Down still scrolls to it.
            outRect.bottom = (int) ((mountain ? 56 : 12) * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
            View card = child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        float pad = (mountain ? 8 : 14) * density;
        // Seven tiles have smaller cell gutters; keep the curved band's edge inside the viewport.
        float side = mountain ? Math.max(0, Math.min(pad,
                Math.min(rect.left, parent.getWidth() - rect.right) - density)) : pad;
        rect.inset(-side, -pad);
        float radius = (mountain ? 22 : 26) * density;
        if (mountain && (shaderTop != rect.top || shaderBottom != rect.bottom)) {
            band.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                    new int[]{0x65DCE5EA, 0x35D0D9E1, 0x55DCE5EA}, null, Shader.TileMode.CLAMP));
            shaderTop = rect.top; shaderBottom = rect.bottom;
        }
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, edge);
    }
}
