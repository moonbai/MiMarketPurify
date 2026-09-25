package com.mars.mimarketpurify.apps

import com.mars.mimarketpurify.hooks.market.AntiSelfDestruct
import com.mars.mimarketpurify.hooks.market.ConfigBackupRestore
import com.mars.mimarketpurify.hooks.market.DetailAds
import com.mars.mimarketpurify.hooks.market.EnableSuperIsland
import com.mars.mimarketpurify.hooks.market.HideSecurityView
import com.mars.mimarketpurify.hooks.market.HideUpdateAll
import com.mars.mimarketpurify.hooks.market.HideFruitEntry
import com.mars.mimarketpurify.hooks.market.HideAutoUpdateSwitch
import com.mars.mimarketpurify.hooks.market.HomeFeed
import com.mars.mimarketpurify.hooks.market.RankAds
import com.mars.mimarketpurify.hooks.market.MainTabAds
import com.mars.mimarketpurify.hooks.market.DetailExtras
import com.mars.mimarketpurify.hooks.market.EntranceAds
import com.mars.mimarketpurify.hooks.market.MineAdGroup
import com.mars.mimarketpurify.hooks.market.TabBadge
import com.mars.mimarketpurify.hooks.market.UpdateDialogBlock
import com.mars.mimarketpurify.hooks.market.MiscApply
import com.mars.mimarketpurify.hooks.market.RecommendSections
import com.mars.mimarketpurify.hooks.market.SearchAds
import com.mars.mimarketpurify.hooks.market.SplashAds
import com.mars.mimarketpurify.hooks.market.FloatingBottomBar
import com.mars.mimarketpurify.hooks.market.TabFilter
import com.mars.mimarketpurify.hooks.market.SubTabFilter
import com.mars.mimarketpurify.hooks.market.UpdateCardSkin
import com.mars.mimarketpurify.hooks.market.MinePageClean
import com.mars.mimarketpurify.hooks.market.UpdateCardUi
import com.mars.mimarketpurify.hooks.market.UpdateDownloadAds
import com.mars.mimarketpurify.init.AppPackage
import com.mars.mimarketpurify.init.AppRegister
import com.mars.mimarketpurify.hooks.market.UpdateTabEntry
import com.mars.mimarketpurify.hooks.market.FloatingAdHook
import io.github.libxposed.api.XposedModuleInterface

object Market : AppRegister() {

    override val packageName: String
        get() = AppPackage.MARKET

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        autoInitHooks(
            param,
            SplashAds,
            MainTabAds,
            HomeFeed,
            SearchAds,
            UpdateDownloadAds,
            DetailAds,
            HideSecurityView,
            HideUpdateAll,
            HideAutoUpdateSwitch,
            HideFruitEntry,
            TabFilter,
            FloatingBottomBar,
            SubTabFilter,
            RankAds,
            MinePageClean,
            UpdateCardSkin,
            UpdateCardUi,
            RecommendSections,
            TabBadge,
            EntranceAds,
            MineAdGroup,
            DetailExtras,
            UpdateDialogBlock,
            EnableSuperIsland,
            MiscApply,
            AntiSelfDestruct,
            UpdateTabEntry,
            ConfigBackupRestore,
            FloatingAdHook
        )
    }
}
