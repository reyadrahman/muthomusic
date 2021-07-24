package com.mutho.music.ui.drawer;

import com.mutho.music.model.Playlist;
import java.util.List;

public interface DrawerView {

    void setPlaylistItems(List<Playlist> playlists);

    void closeDrawer();

    void setDrawerItemSelected(@DrawerParent.Type int type);
}
