package com.stashhunter;

import com.stashhunter.modules.StashFinder;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class StashHunterAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("StashHunter");

    @Override
    public void onInitialize() {
        Modules.get().add(new StashFinder());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.stashhunter";
    }
}
