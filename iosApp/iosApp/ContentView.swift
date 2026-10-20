import SwiftUI

struct ContentView: View {
    var body: some View {
        ZStack {
            Color(red: 0.106, green: 0.071, blue: 0.063).ignoresSafeArea()
            VStack(spacing: 12) {
                Text("斗地主 · 局域网")
                    .font(.largeTitle.bold())
                    .foregroundColor(Color(red: 0.831, green: 0.686, blue: 0.216))
                Text("iOS 首期仅验证 KMP 模块编译（M5）")
                    .foregroundColor(.white.opacity(0.8))
                Text("共享规则 / AI / 协议已在 :shared 中")
                    .font(.footnote)
                    .foregroundColor(.white.opacity(0.5))
            }
        }
    }
}

#Preview {
    ContentView()
}
