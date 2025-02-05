import shared

class SwitUIInAppNotification: InAppNotification {
    func onError(message _: shared.Message, throwable _: KotlinThrowable) {}

    func onProgress(message _: shared.Message, progress _: Int32, total _: Int32) {}

    func onSuccess(message _: shared.Message) {}
}
