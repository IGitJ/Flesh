package com.ecjtu.flesh.mvp.presenter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.preference.PreferenceManager
import android.text.format.Formatter
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ashokvarma.bottomnavigation.BottomNavigationBar
import com.ashokvarma.bottomnavigation.BottomNavigationItem
import com.bumptech.glide.Glide
import com.ecjtu.componentes.activity.AppThemeActivity
import com.ecjtu.flesh.R
import com.ecjtu.flesh.userinterface.activity.MainActivity
import com.ecjtu.flesh.userinterface.adapter.TabPagerAdapter
import com.ecjtu.flesh.userinterface.dialog.GetVipDialogHelper
import com.ecjtu.flesh.userinterface.dialog.SyncInfoDialogHelper
import com.ecjtu.flesh.userinterface.fragment.*
import com.ecjtu.flesh.util.activity.ActivityUtil
import com.ecjtu.flesh.util.admob.AdmobCallback
import com.ecjtu.flesh.util.admob.AdmobManager
import com.ecjtu.flesh.util.file.FileUtil
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import java.io.File
import kotlin.concurrent.thread
import kotlin.reflect.KClass


/**
 * Created by KerriGan on 2017/6/2.
 */
class MainActivityDelegate(owner: MainActivity) : Delegate<MainActivity>(owner), BaseTabPagerFragment.IDelegate {

    private val mFloatButton = owner.findViewById<View>(R.id.float_button) as FloatingActionButton
    private val mViewPager = owner.findViewById<View>(R.id.view_pager) as androidx.viewpager.widget.ViewPager
    private val mTabLayout = owner.findViewById<View>(R.id.tab_layout) as TabLayout
    private val mAppbarLayout = owner.findViewById<View>(R.id.app_bar) as AppBarLayout
    private var mAppbarExpand = true
    private var mCurrentPagerIndex = 0
    private var mBottomNav: BottomNavigationBar? = null

    var mAdMob: AdmobManager? = null

    init {
        loadAd()
        mViewPager.adapter = FragmentAdapter(owner.supportFragmentManager)
        mViewPager.addOnPageChangeListener(object : androidx.viewpager.widget.ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            }

            override fun onPageSelected(position: Int) {
                Log.i("FragmentAdapter", "onPageSelected position $position")
                when (position) {
                    0 -> {
                        mTabLayout.visibility = View.VISIBLE
                    }
                    1 -> {
                        mTabLayout.visibility = View.GONE
                    }
                }
                mBottomNav?.selectTab(position, false)
                if (mViewPager.adapter is androidx.fragment.app.FragmentPagerAdapter) {
                    val fragment = (mViewPager.adapter as androidx.fragment.app.FragmentPagerAdapter).getItem(position)
                    if (fragment is BaseTabPagerFragment) {
                        fragment.onSelectTab()
                    }
                }
            }
        })

        initView()
        recoverTab(0, isAppbarLayoutExpand())
    }

    private fun initView() {
        val cacheSize = PreferenceManager.getDefaultSharedPreferences(owner).getLong(com.ecjtu.flesh.Constants.PREF_CACHE_SIZE, com.ecjtu.flesh.Constants.DEFAULT_GLIDE_CACHE_SIZE)
        val cacheStr = Formatter.formatFileSize(owner, cacheSize)
        val glideSize = FileUtil.getGlideCacheSize(owner)
        val glideStr = Formatter.formatFileSize(owner, glideSize)
        val textView = findViewById(R.id.size) as TextView?
        mBottomNav = findViewById(R.id.bottom_navigation_bar) as BottomNavigationBar

        textView?.let {
            textView.setText(String.format("%s/%s", glideStr, cacheStr))
        }
        mFloatButton.setOnClickListener {
            doFloatButton(mBottomNav!!)
        }

        findViewById(R.id.like)?.setOnClickListener {
            val intent = AppThemeActivity.newInstance(owner, PageLikeFragment::class.java)
            owner.startActivity(intent)
            val drawerLayout = findViewById(R.id.drawer_layout) as androidx.drawerlayout.widget.DrawerLayout
            drawerLayout.closeDrawer(Gravity.LEFT)
        }

        findViewById(R.id.cache)?.setOnClickListener {
            val cacheFile = File(owner.cacheDir.absolutePath + "/image_manager_disk_cache")
            val list = FileUtil.getFilesByFolder(cacheFile)
            var ret = 0L
            for (child in list) {
                ret += child.length()
            }
            val size = Formatter.formatFileSize(owner, ret)
            AlertDialog.Builder(owner).setTitle(R.string.cache_size).setMessage(owner.getString(R.string.cached_data_cleaned_or_not, size))
                    .setPositiveButton(R.string.ok, { dialog, which -> thread { Glide.get(owner).clearDiskCache() } })
                    .setNegativeButton(R.string.cancel, null)
                    .create().show()
        }

        findViewById(R.id.disclaimer)?.setOnClickListener {
            AlertDialog.Builder(owner).setTitle(R.string.statement).setMessage(R.string.statement_content)
                    .setPositiveButton(R.string.ok, null)
                    .create().show()
        }

        findViewById(R.id.history)?.setOnClickListener {
            val intent = AppThemeActivity.newInstance(owner, PageHistoryFragment::class.java)
            owner.startActivity(intent)
            val drawerLayout = findViewById(R.id.drawer_layout) as androidx.drawerlayout.widget.DrawerLayout
            drawerLayout.closeDrawer(Gravity.LEFT)
        }

        findViewById(R.id.vip_info)?.setOnClickListener {
            GetVipDialogHelper(owner).getDialog()?.show()
        }

        findViewById(R.id.sync_info)?.setOnClickListener {
            SyncInfoDialogHelper(owner).getDialog()?.show()
        }

        findViewById(R.id.feedback)?.setOnClickListener {
            try {
                ActivityUtil.jumpToMarket(owner, owner.packageName)
            } catch (ex: Exception) {
                ex.printStackTrace()
                try {
                    ActivityUtil.openUrlByBrowser(owner, "https://play.google.com/store/apps/details?id=com.ecjtu.flesh")
                } catch (ex: Exception) {
                }
            }
        }

        mAppbarExpand = PreferenceManager.getDefaultSharedPreferences(owner).getBoolean(TabPagerAdapter.KEY_APPBAR_LAYOUT_COLLAPSED, false)

        mAppbarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            if (verticalOffset == 0) {
                mAppbarExpand = true
            } else if (verticalOffset == -(appBarLayout.height - mTabLayout.height)) {
                mAppbarExpand = false
            }
        })

        mBottomNav!!
                .addItem(BottomNavigationItem(R.drawable.ic_image, "Image"))
                .addItem(BottomNavigationItem(R.drawable.ic_video, "Video"))
//                .addItem(BottomNavigationItem(R.drawable.ic_girl, "More"))
                .initialise()
        mBottomNav!!.setTabSelectedListener(object : BottomNavigationBar.OnTabSelectedListener {
            override fun onTabUnselected(position: Int) {
                if (mViewPager.adapter is androidx.fragment.app.FragmentPagerAdapter) {
                    val fragment = (mViewPager.adapter as androidx.fragment.app.FragmentPagerAdapter).getItem(position)
                    if (fragment is BaseTabPagerFragment) {
                        fragment.onUnSelectTab()
                    }
                }
            }

            @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
            override fun onTabSelected(position: Int) {
                mCurrentPagerIndex = position
//                mViewPager.adapter?.apply {
//                    (this as TabPagerAdapter).onStop(owner, mTabLayout.selectedTabPosition, isAppbarLayoutExpand())
//                }
                when (position) {
                    0 -> {
                        mViewPager.setCurrentItem(0)
                    }

                    1 -> {
                        mViewPager.setCurrentItem(1)
                    }
                }
                //store view states
                //mViewPager.adapter?.notifyDataSetChanged()
            }

            override fun onTabReselected(position: Int) {
            }

        })
    }

    fun onStop() {
        mAdMob?.onPause()
    }

    fun onResume() {
        mAdMob?.onResume()
    }

    fun onDestroy() {
        mAdMob?.onDestroy()
    }

    override fun isAppbarLayoutExpand(): Boolean = mAppbarExpand

    fun convertView2Bitmap(view: View, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun doFloatButton(bottomNavigationBar: BottomNavigationBar) {
        bottomNavigationBar.hide()
        val position = mTabLayout.selectedTabPosition
        var recyclerView: RecyclerView? = null
        var size = 0
        mViewPager.adapter?.let {
            val fragment = (mViewPager.adapter as FragmentAdapter).getItem(mViewPager.currentItem)
            val viewPager = (fragment as BaseTabPagerFragment).getViewPager()
            val tabPager = viewPager?.adapter ?: return@doFloatButton
            recyclerView = (tabPager as TabPagerAdapter).getViewStub(position) as RecyclerView?
            size = tabPager.getListSize(position)
        }
        val snake = Snackbar.make(findViewById(R.id.content)!!, "", Snackbar.LENGTH_SHORT)
        if (snake.view is LinearLayout) {
            val vg = snake.view as LinearLayout
            val layout = LayoutInflater.from(owner).inflate(R.layout.layout_quick_jump, vg, false) as ViewGroup

            val local = layout.findViewById<View>(R.id.seek_bar) as SeekBar
            val pos = layout.findViewById<View>(R.id.position) as TextView

            val listener = { v: View ->
                if (position != mTabLayout.selectedTabPosition) {
                    snake.dismiss()
                } else {
                    when (v.id) {
                        R.id.top -> {
                            recyclerView?.let {
                                (recyclerView?.layoutManager as LinearLayoutManager).scrollToPosition(0)
                            }
                        }

                        R.id.mid -> {
                            recyclerView?.let {
                                var jumpPos = Integer.valueOf(pos.text.toString()) - 2
                                if (jumpPos < 0) jumpPos = 0
                                (recyclerView?.layoutManager as LinearLayoutManager).scrollToPosition(jumpPos)
                            }
                        }

                        R.id.bottom -> {
                            recyclerView?.let {
                                (recyclerView?.layoutManager as LinearLayoutManager).scrollToPosition(size - 2)
                            }
                        }
                    }
                    snake.dismiss()
                }
                Unit
            }
            layout.findViewById<View>(R.id.top).setOnClickListener(listener)
            layout.findViewById<View>(R.id.mid).setOnClickListener(listener)
            layout.findViewById<View>(R.id.bottom).setOnClickListener(listener)

            local.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    pos.setText(progress.toString())
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                }
            })
            local.max = size
            if (recyclerView != null) {
                val curPos = (recyclerView?.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
                local.progress = curPos
            }
            layout.findViewById<View>(R.id.mid).setOnClickListener(listener)
            vg.addView(layout)
        }
        snake.show()
    }

    fun recoverTab(tabItem: Int, isExpand: Boolean) {
        mViewPager.setCurrentItem(tabItem)
        mAppbarLayout.setExpanded(isExpand)
    }

    fun getLastTabItem(clazz: KClass<out TabPagerAdapter>): Int = PreferenceManager.getDefaultSharedPreferences(owner).getInt(TabPagerAdapter.KEY_LAST_TAB_ITEM + "_" + clazz.java.simpleName, 0)

    fun changeViewPager(index: Int) {
//        mTabLayout.removeAllTabs()
//        mTabLayout.setupWithViewPager((mViewPager.adapter as FragmentPagerAdapter).getItem(index))
    }

    override fun getTabLayout(): TabLayout {
        return mTabLayout
    }

    private fun loadAd() {
        mAdMob = AdmobManager(owner)
        mAdMob?.loadInterstitialAd(owner.getString(R.string.admob_chaye), object : AdmobCallback {
            override fun onLoaded() {
                Log.i("MainActivityDelegate", "AdMob onLoaded")
                mAdMob?.getLatestInterstitialAd()?.show()
            }

            override fun onError(code: Int) {
                Log.i("MainActivityDelegate", "AdMob onError $code")
            }

            override fun onOpened() {
                Log.i("MainActivityDelegate", "AdMob onOpened")
            }

            override fun onClosed() {
                Log.i("MainActivityDelegate", "AdMob onClosed")
            }

        })
    }

    inner class FragmentAdapter(fm: androidx.fragment.app.FragmentManager) : androidx.fragment.app.FragmentPagerAdapter(fm) {
        val fragments = Array<androidx.fragment.app.Fragment?>(2) { int ->
            when (int) {
                0 -> {
                    Log.i("FragmentAdapter", "new MzituFragment")
                    MzituFragment()
                }
                1 -> {
                    Log.i("FragmentAdapter", "new VideoTabFragment")
                    VideoTabFragment()
                }
                else -> {
                    null
                }
            }
        }

        init {
        }

        override fun getItem(position: Int): androidx.fragment.app.Fragment {
            Log.i("FragmentAdapter", "getItem position $position id " + fragments[position]!!.toString())
            return fragments[position]!!
        }

        override fun getCount(): Int {
            return fragments.size
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val ret = super.instantiateItem(container, position)
            Log.i("FragmentAdapter", "instantiateItem position $position")
            if (ret is BaseTabPagerFragment) {
//                ret.setDelegate(this@MainActivityDelegate)
                ret.setTabLayout(getTabLayout())
                fragments[position] = ret
            }
            return ret
        }
    }
}