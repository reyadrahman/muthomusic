package com.mutho.music.ui.adapters;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.util.SparseArray;
import android.view.ViewGroup;
import com.annimon.stream.IntStream;

public class PagerAdapter extends FragmentPagerAdapter {

    private static final String ARG_PAGE_TITLE = "page_title";

    private FragmentManager fragmentManager;

    private SparseArray<Fragment> fragmentMap = new SparseArray<>();

    public PagerAdapter(FragmentManager fragmentManager) {
        super(fragmentManager);
        this.fragmentManager = fragmentManager;
    }

    @Override
    public Fragment getItem(int position) {
        return fragmentMap.get(position);
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        Fragment fragment = (Fragment) super.instantiateItem(container, position);
        fragmentMap.put(position, fragment);
        return fragment;
    }

    @Override
    public int getCount() {
        return fragmentMap.size();
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return getItem(position).getArguments().getString(ARG_PAGE_TITLE);
    }

    public void addFragment(Fragment fragment) {
        fragmentMap.put(fragmentMap.size(), fragment);
        notifyDataSetChanged();
    }

    public void clear() {
        fragmentMap.clear();

        notifyDataSetChanged();
    }

    public void removeAllChildFragments() {
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        IntStream.range(0, fragmentMap.size()).forEach(value -> fragmentTransaction.remove(fragmentMap.get(value)));
        fragmentTransaction.commitAllowingStateLoss();

        clear();
    }
}
