package mobile

// Pulls sing-box's libbox into this module so `gomobile bind` can bind it in the SAME
// AAR as the mihomo façade (two gomobile AARs cannot coexist: each carries the Go runtime).
import _ "github.com/sagernet/sing-box/experimental/libbox"
