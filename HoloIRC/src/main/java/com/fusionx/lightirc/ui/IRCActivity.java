/*
    HoloIRC - an IRC client for Android

    Copyright 2013 Lalit Maganti

    This file is part of HoloIRC.

    HoloIRC is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    HoloIRC is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with HoloIRC. If not, see <http://www.gnu.org/licenses/>.
 */

package com.fusionx.lightirc.ui;

import com.astuetz.PagerSlidingTabStrip;
import com.fusionx.lightirc.R;
import com.fusionx.lightirc.constants.FragmentTypeEnum;
import com.fusionx.lightirc.ui.widget.DrawerToggle;
import com.fusionx.lightirc.util.UIUtils;
import com.fusionx.relay.Channel;
import com.fusionx.relay.ChannelUser;
import com.fusionx.relay.PrivateMessageUser;
import com.fusionx.relay.Server;
import com.fusionx.relay.ServerConfiguration;
import com.fusionx.relay.communication.ServerEventBus;
import com.fusionx.relay.constants.UserListChangeType;
import com.fusionx.relay.event.ChannelEvent;
import com.fusionx.relay.event.ConnectedEvent;
import com.fusionx.relay.event.DisconnectEvent;
import com.fusionx.relay.event.MentionEvent;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;
import com.squareup.otto.Subscribe;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

import java.util.Collection;
import java.util.List;

import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;

/**
 * Activity which contains all the communication code between the fragments It also implements a lot
 * of callbacks to stop exposing objects to the fragments
 *
 * @author Lalit Maganti
 */
public abstract class IRCActivity extends ActionBarActivity implements UserListFragment.Callbacks,
        ServiceFragment.ServiceFragmentCallback, ActionsPagerFragment
                .ActionsPagerFragmentCallback, IRCPagerFragment.IRCPagerInterface {

    /*
     * Listener used when the view pages changes pages
     */
    private final ViewPager.SimpleOnPageChangeListener mListener = new ViewPager
            .SimpleOnPageChangeListener() {
        @Override
        public void onPageSelected(final int position) {
            supportInvalidateOptionsMenu();
            closeAllSlidingMenus();

            mActionsPagerFragment.onPageChanged(mIRCPagerFragment.getCurrentType());

            if (mActionsSlidingMenu != null) {
                mActionsSlidingMenu.setTouchModeAbove(position == 0 ? SlidingMenu
                        .TOUCHMODE_FULLSCREEN : SlidingMenu.TOUCHMODE_MARGIN);
            }
            mUserSlidingMenu.setTouchModeAbove(position == 0 ? SlidingMenu.TOUCHMODE_NONE :
                    SlidingMenu.TOUCHMODE_MARGIN);
        }
    };

    private final Object mEventReceiver = new Object() {

        @Subscribe
        public void onDisconnected(final DisconnectEvent event) {
            if (event.userTriggered) {
                mServiceFragment.removeServiceReference(mServerTitle);

                final Intent intent = new Intent(IRCActivity.this, ServerListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            } else {
                getSupportActionBar().setSubtitle(getString(R.string.status_disconnected));
                closeAllSlidingMenus();
                mIRCPagerFragment.onUnexpectedDisconnect();
                mActionsPagerFragment.updateConnectionStatus(false);
                if (!event.retryPending && getServer() != null) {
                    mServiceFragment.removeServiceReference(mServerTitle);
                }
            }
        }

        @Subscribe
        public void onServerConnected(final ConnectedEvent event) {
            mActionsPagerFragment.updateConnectionStatus(true);
            getSupportActionBar().setSubtitle(getServer().getStatus());
        }

        @Subscribe
        public void onChannelMessage(final ChannelEvent event) {
            if (event.user != null && event.changeType != UserListChangeType.NONE) {
                if (mUserSlidingMenu.isMenuShowing()) {
                    onUserListDisplayed();
                }
            }
        }

        @Subscribe
        public void onMention(final MentionEvent event) {
            if (!mIRCPagerFragment.getCurrentTitle().equals(event.destination)) {
                final String message = String.format(getString(R.string.activity_mentioned),
                        event.destination);
                final de.keyboardsurfer.android.widget.crouton.Configuration.Builder builder = new
                        de.keyboardsurfer.android.widget.crouton.Configuration.Builder();
                builder.setDuration(2000);
                final Crouton crouton = Crouton.makeText(IRCActivity.this, message,
                        Style.INFO).setConfiguration(builder.build());
                crouton.show();
            }
        }
    };

    protected ActionsPagerFragment mActionsPagerFragment;

    protected SlidingMenu mActionsSlidingMenu;

    protected DrawerToggle mDrawerToggle;

    // Sliding menus
    protected SlidingMenu mUserSlidingMenu;

    // The Fragments
    private ServiceFragment mServiceFragment;

    private UserListFragment mUserListFragment;

    private IRCPagerFragment mIRCPagerFragment;

    // Other objects
    private String mServerTitle;

    // Do not do any IRC work here - views may not have been set up, activities not instantiated
    // etc.
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        setTheme(UIUtils.getThemeInt());

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_irc);

        final ServerConfiguration.Builder builder = getIntent().getParcelableExtra("server");
        mServerTitle = builder != null ? builder.getTitle() : getIntent().getStringExtra
                ("serverTitle");

        final FragmentManager fm = getSupportFragmentManager();

        setUpSlidingMenu(fm);

        mIRCPagerFragment = (IRCPagerFragment) fm.findFragmentById(R.id.pager_fragment);
        mServiceFragment = (ServiceFragment) fm.findFragmentByTag("service");

        final boolean isFirstStart = mServiceFragment == null;
        final ActionBar actionBar = getSupportActionBar();
        if (isFirstStart) {
            mServiceFragment = new ServiceFragment();
            fm.beginTransaction().add(mServiceFragment, "service").commit();
            actionBar.setSubtitle(getString(R.string.status_connecting));
        } else if (getServer() != null) {
            final ServerEventBus bus = getServer().getServerEventBus();
            bus.register(mEventReceiver);
            actionBar.setSubtitle(getServer().getStatus());
        }
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(mServerTitle);

        final PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) findViewById(R.id
                .pager_tabs);
        tabs.setOnPageChangeListener(mListener);
        tabs.setTextColorResource(android.R.color.white);
    }

    private void setUpSlidingMenu(final FragmentManager manager) {
        mUserSlidingMenu = (SlidingMenu) findViewById(R.id.user_sliding_menu);
        mUserSlidingMenu.setContent(R.layout.view_pager_fragment);
        mUserSlidingMenu.setMenu(R.layout.sliding_menu_fragment_userlist);
        mUserSlidingMenu.setShadowDrawable(R.drawable.shadow);
        mUserSlidingMenu.setTouchModeAbove(SlidingMenu.TOUCHMODE_NONE);
        mUserSlidingMenu.setTouchmodeMarginThreshold(10);
        mUserSlidingMenu.setMode(SlidingMenu.RIGHT);
        mUserSlidingMenu.setBehindWidthRes(R.dimen.user_menu_sliding_width);

        mUserListFragment = (UserListFragment) manager.findFragmentById(R.id.userlist_fragment);

        mUserSlidingMenu.setOnOpenListener(new SlidingMenu.OnOpenListener() {
            @Override
            public void onOpen() {
                mUserListFragment.onMenuOpened(getServer().getUserChannelInterface()
                        .getChannel(mIRCPagerFragment.getCurrentTitle()));
                onUserListDisplayed();
            }
        });
        mUserSlidingMenu.setOnCloseListener(new SlidingMenu.OnCloseListener() {
            @Override
            public void onClose() {
                getSupportActionBar().setSubtitle(getServer().getStatus());
                mUserListFragment.onClose();
            }
        });

        setUpActionsFragment();
    }

    // This is different for tablets and phones so get subclasses to do the work
    protected abstract void setUpActionsFragment();

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (mDrawerToggle != null) {
            // Sync the toggle state after onRestoreInstanceState has occurred.
            mDrawerToggle.syncState();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Call this regardless of whether this is a resumption or not - the correct checks to
        // whether to bind to the service will be done in the ServiceFragment
        mServiceFragment.connectToServer(this, mServerTitle);

        if (getServer() != null) {
            getServer().getServerEventBus().setDisplayed(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        getServer().getServerEventBus().setDisplayed(false);
        getServer().getServerCache().setIrcTitle(mIRCPagerFragment.getCurrentTitle());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    protected void onDestroy() {
        Crouton.clearCroutonsForActivity(this);
        getServer().getServerEventBus().unregister(mEventReceiver);

        super.onDestroy();
    }

    // Options Menu stuff
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.activity_server_channel_ab, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        final MenuItem userMenu = menu.findItem(R.id.activity_server_channel_ab_users);
        final boolean isChannel = (FragmentTypeEnum.Channel.equals(mIRCPagerFragment
                .getCurrentType()));
        userMenu.setVisible(isChannel && mUserSlidingMenu != null);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (mDrawerToggle == null || !mDrawerToggle.onOptionsItemSelected(item)) {
            switch (item.getItemId()) {
                case R.id.activity_server_channel_ab_users:
                    mUserSlidingMenu.toggle();
                    return true;
                default:
                    return false;
            }
        } else {
            return true;
        }
    }
    // Options menu end

    void onUserListDisplayed() {
        getSupportActionBar().setSubtitle(mUserListFragment.getRealAdapter().getCount() + " users");
    }

    @Override
    public void onServerAvailable(final Server server) {
        final ServerEventBus bus = server.getServerEventBus();

        // Register and then display
        bus.register(mEventReceiver);
        bus.setDisplayed(true);

        bus.register(mIRCPagerFragment);
    }

    @Override
    public void onSetupViewPager() {
        mIRCPagerFragment.onCreateServerFragment(mServerTitle);
        getSupportActionBar().setSubtitle(getServer().getStatus());

        if (isConnectedToServer()) {
            final String tabTitle = getServer().getServerCache().getIrcTitle();
            for (final Channel channel : getServer().getUser().getChannels()) {
                final boolean switchToTab = tabTitle.equals(channel.getName());
                mIRCPagerFragment.onCreateChannelFragment(channel.getName(), switchToTab);
            }
            final Collection<PrivateMessageUser> privateMessages = getServer().getUser()
                    .getPrivateMessages();
            for (final PrivateMessageUser user : privateMessages) {
                final boolean switchToTab = tabTitle.equals(user.getNick());
                mIRCPagerFragment.onCreateMessageFragment(user.getNick(), switchToTab);
            }
            // Do this so that the options menu can pick up whether to display the user button
            // or not
            supportInvalidateOptionsMenu();
        }
    }

    @Override
    public Server getServer() {
        return mServiceFragment.getServer();
    }

    @Override
    public void closeAllSlidingMenus() {
        if (mActionsSlidingMenu != null) {
            mActionsSlidingMenu.showContent();
        }
        mUserSlidingMenu.showContent();
    }

    @Override
    public boolean isUserSlidingMenuOpen() {
        return mUserSlidingMenu.isMenuShowing();
    }

    @Override
    public boolean isConnectedToServer() {
        final Server server = getServer();
        return server != null && server.isConnected();
    }

    @Override
    public void onUserMention(final List<ChannelUser> users) {
        mIRCPagerFragment.onMentionRequested(users);
        closeAllSlidingMenus();
    }

    @Override
    public String getNick() {
        return getServer().getUser().getNick();
    }

    @Override
    public void onRemoveCurrentFragment() {
        final Server server = getServer();
        if (FragmentTypeEnum.User.equals(mIRCPagerFragment.getCurrentType())) {
            // We want to remove the fragment before sending the close message to prevent a NPE
            // when the UserFragment tries to set caching to false
            mIRCPagerFragment.onRemoveFragment(mIRCPagerFragment.getCurrentTitle());

            server.getServerCallBus().sendClosePrivateMessage(mIRCPagerFragment.getCurrentTitle());
        } else {
            server.getServerCallBus().sendPart(mIRCPagerFragment.getCurrentTitle());
        }
    }

    // Getters and setters
    @Override
    public String getServerTitle() {
        return mServerTitle;
    }
}