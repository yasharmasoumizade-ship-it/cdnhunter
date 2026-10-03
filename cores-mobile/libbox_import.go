package mobile

// One gomobile AAR must hold BOTH engines: every gomobile AAR carries its own Go runtime
// (libgojni.so + go.Seq), and the app allows only one. This pulls sing-box's libbox into the same
// module so `gomobile bind` binds it next to the mihomo façade (mobile.go, copied in by CI from
// mihomo-mobile/, which stays the single source of the façade).
import _ "github.com/sagernet/sing-box/experimental/libbox"
