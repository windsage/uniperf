package mtkServices

import (
	"android/soong/android"
	"android/soong/java"
)

func mtkServicesDefaults(ctx android.LoadHookContext) {
	type props struct {
		Libs        []string
		Static_libs []string
	}
	p := &props{}
	vars := ctx.Config().VendorConfig("mtkPlugin")
	//if vars.Bool("MSSI_MTK_VOW_2E2K_SUPPORT") {
	p.Static_libs = append(p.Static_libs, "services.vow")
	//}
	if android.ExistentPathForSource(ctx, "vendor/mediatek/proprietary/frameworks/opt/fm/Android.bp").Valid() {
		p.Static_libs = append(p.Static_libs, "services.fmradioservice")
		p.Libs = append(p.Libs, "mediatek-fm-framework")
	}
	if vars.String("MSSI_MTK_TELEPHONY_ADD_ON_POLICY") != "1" {
		p.Static_libs = append(p.Static_libs, "mediatek-framework-net")
	}
	//mbrainMode := strings.ToLower(vars.String("MTK_MBRAIN_MODE"))
	//if strings.Contains(mbrainMode, "mtk") {
		p.Static_libs = append(p.Static_libs, "services.mbrainlocalservice")
	//}
	ctx.AppendProperties(p)
}

func init() {
	android.RegisterModuleType("mediatek_services_defaults", mtkServicesDefaultsFactory)
}

func mtkServicesDefaultsFactory() android.Module {
	module := java.DefaultsFactory()
	android.AddLoadHook(module, mtkServicesDefaults)
	return module
}
