package xyz.syzygylabs.urbanroute;

import android.content.Context;
import android.widget.ImageView;

import com.oguzdev.circularfloatingactionmenu.library.FloatingActionButton;
import com.oguzdev.circularfloatingactionmenu.library.FloatingActionMenu;

public class UserInterface {

    private FloatingActionButton actionButton;
    private FloatingActionMenu actionMenu;

    ImageView icon;




    public UserInterface(Context context){
        icon  = new ImageView(context); // Create an icon
        icon.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_report_white_48dp));
        actionButton = new FloatingActionButton.Builder(get)
                .setContentView(icon)
                .setTheme(FloatingActionButton.THEME_DARK)
                .build();
    }

}
