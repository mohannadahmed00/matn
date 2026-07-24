import SwiftUI
import Shared

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        .onChange(of: scenePhase) { phase in
            // T039 (FR-009): backgrounding hook — one call, no business logic (Principle IV).
            if phase == .background {
                MatnKoinStarterKt.flushSessionState()
            }
        }
    }
}