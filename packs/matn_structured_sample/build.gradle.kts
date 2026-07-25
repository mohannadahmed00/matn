plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("matn_structured_sample")
    dynamicDelivery {
        deliveryType.set("on-demand")
    }
}