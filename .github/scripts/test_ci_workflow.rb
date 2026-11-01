require 'yaml'
require 'set'

root = ARGV.fetch(0, File.expand_path('../..', __dir__))
load_yaml = YAML.respond_to?(:unsafe_load_file) ? YAML.method(:unsafe_load_file) : YAML.method(:load_file)
workflow = load_yaml.call(File.join(root, '.github/workflows/android.yml'))
project = load_yaml.call(File.join(root, 'appleApp/project.yml'))
jobs = workflow.fetch('jobs')

def check(condition, message)
  raise message unless condition
end

def commands(job)
  job.fetch('steps').map { |step| step['run'] || step.dig('with', 'script') }.compact.join("\n")
end

linux = %w[build-linux build-web build-fdroid]
apple = %w[build-ios build-macos]
builds = linux + apple + %w[build-windows]
check(jobs.keys.to_set == (builds + %w[lint upload]).to_set, 'CI jobs changed without preserving the validation graph')
builds.each do |id|
  check(Array(jobs.fetch(id).fetch('needs')) == ['lint'], "#{id} must start after the fast lint gate")
  check(jobs.fetch(id).fetch('if').include?("github.event_name == 'push'"), "Tag builds must include #{id}")
end
dispatches = {
  'all' => builds,
  'linux' => linux,
  'apple' => apple,
  'android-desktop' => %w[build-linux],
  'ios' => %w[build-ios],
  'macos' => %w[build-macos],
  'windows' => %w[build-windows],
  'web' => %w[build-web],
  'fdroid' => %w[build-fdroid],
}
triggers = workflow['on'] || workflow[true]
check(triggers.dig('workflow_dispatch', 'inputs', 'target', 'options').to_set == dispatches.keys.to_set, 'Every supported dispatch target must be selectable')
dispatches.each do |target, expected|
  selected = builds.select { |id| jobs.fetch(id).fetch('if').scan(/inputs\.target == '([^']+)'/).flatten.include?(target) }
  check(selected.to_set == expected.to_set, "The #{target} dispatch must preserve its platform checks")
end
groups = builds.map do |id|
  concurrency = jobs.fetch(id).fetch('concurrency')
  group = concurrency.fetch('group')
  check(group.include?('${{ github.workflow }}-${{ github.ref }}-job-') && !group.include?('github.job'), "#{id} must deduplicate runs of the same platform and ref")
  check(concurrency.fetch('cancel-in-progress') == "${{ !startsWith(github.ref, 'refs/tags/') }}", "#{id} must cancel obsolete branch builds without canceling release jobs")
  group
end
check(groups.uniq.size == builds.size, 'Independent platforms must not cancel each other')
check(jobs.fetch('upload').fetch('needs').to_set == (builds + ['lint']).to_set, 'Publishing must wait for every validation job')
check(jobs.fetch('upload').fetch('if') == "startsWith(github.ref, 'refs/tags/')", 'Publishing must remain tag-only')
publishers = jobs.select { |_, job| job.fetch('steps').any? { |step| step.fetch('uses', '').start_with?('r0adkll/upload-google-play@') } }
check(publishers.keys == ['upload'], 'Google Play publishing must wait for all checks')

{ 'build-ios' => 'iosSimulatorArm64', 'build-macos' => 'macosArm64' }.each do |id, target|
  steps = jobs.fetch(id).fetch('steps')
  test = steps.find { |step| step.fetch('run', '').include?("./gradlew #{target}Test ") }
  check(test && test.fetch('run').include?('--no-daemon'), "#{target} tests must run in the app job with an independent JVM")
  build = steps.index { |step| step.fetch('run', '').match?(/xcodebuild build-for-testing|:apple-shared:compileKotlinMacosArm64/) }
  check(build && steps.index(test) < build, "#{target} tests must populate library outputs before the app build")
  reports = steps.find { |step| step.dig('with', 'name') == "apple-#{target}-test-reports" }
  check(reports && reports['if'] == 'always()', "#{target} reports must remain available after failures")
  check(reports.dig('with', 'path').lines.map(&:strip).to_set == %w[**/build/test-results/** **/build/reports/tests/**].to_set, "#{target} reports must retain test results and HTML reports")
end

macos = jobs.fetch('build-macos').fetch('steps')
compile = macos.find { |step| step.fetch('run', '').include?(':apple-shared:compileKotlinMacosArm64') }
link = macos.find { |step| step.fetch('run', '').include?(':apple-shared:linkReleaseFrameworkMacosArm64') }
check(compile && link && macos.index(compile) < macos.index(link), 'Compile native libraries before linking in a separate step')
check([compile, link].all? { |step| step.fetch('run').include?('--no-daemon') }, 'Native library compilation and Release linking must use fresh JVMs')

android = commands(jobs.fetch('build-linux'))
classes = android.match(/-Pandroid\.testInstrumentationRunnerArguments\.class=([^\s]+)/)[1].split(',')
expected_classes = %w[
  dev.dimension.flare.ui.screen.settings.ProxySettingsContentTest
  dev.dimension.flare.ui.screen.settings.PluginManagementMenuTest
  dev.dimension.flare.common.WebViewProxyTest
  dev.dimension.flare.ui.screen.gallery.GalleryDetailScreenTest
  dev.dimension.flare.ui.screen.media.ImageTranslationErrorDialogTest
]
check(classes.to_set == expected_classes.to_set, 'The combined Android invocation must retain all five regression classes')
check(android.scan(':app:connectedDebugAndroidTest').size == 1, 'Android regressions must use one instrumentation invocation')
check(android.include?('-x wasmJsBrowserTest'), 'Linux unit tests must delegate Wasm execution to the Web job')
web = commands(jobs.fetch('build-web'))
%w[:web-shared:wasmJsTest :shared:wasmJsBrowserTest].each do |task|
  check(web.include?(task), "The Web job must retain #{task}")
end
check(web.include?("--tests '*ProxyEngineCapabilitiesTest*'"), 'The shared browser proxy regression must remain selected')

ios = commands(jobs.fetch('build-ios'))
expected_selectors = %w[
  TimelineControllerTests/ProxyPlaybackLifecycleTests
  TimelineControllerTests/ProxyImageLifecycleTests
  TimelineControllerTests/WebViewProxyTests
  TimelineControllerTests/LoginCookieSnapshotTests
  TimelineControllerTests/UgoiraPlaybackTests
  iOSTests/VideoPlaybackSessionTests
  iOSTests/MediaPlaybackMemoryTests
  iOSTests/TimelinePlaybackCoordinatorTests
  iOSTests/MediaTransferCancellationTests
  MediaViewerInteractionTests/MediaViewerInteractionTests/testTranslationErrorDetailsAreOnlyAccessibleFromFailureAlertWithPost
  MediaViewerInteractionTests/MediaViewerInteractionTests/testTranslationErrorDetailsAreOnlyAccessibleFromFailureAlertWithoutPost
]
check(ios.scan(/-only-testing:([^\s]+)/).flatten.to_set == expected_selectors.to_set, 'iOS regression coverage must remain unchanged')
check(ios.scan('xcodebuild build-for-testing').size == 1, 'iOS regression targets must be built once')
check(ios.scan('xcodebuild test-without-building').size == 2 && !ios.match?(/xcodebuild test\s/), 'iOS regressions must reuse the built products')
check(ios.scan('-scheme CI-iOS').size == 3, 'iOS builds and tests must use the same scheme')
check(ios.scan('-derivedDataPath build/DerivedData').size == 3, 'iOS builds and tests must use the same products directory')

targets = project.fetch('targets')
owners = targets.select { |_, target| Array(target['preBuildScripts']).any? { |script| script['name'] == 'Compile Kotlin' } }.keys
check(owners == ['FlareAppleCore'], 'FlareAppleCore must be the sole Kotlin framework build owner')
%w[iOS macOS FlareAppleUI iOSTests TimelineControllerTests MediaViewerInteractionTests].each do |name|
  pending = [name]
  visited = Set.new
  until pending.empty?
    current = pending.shift
    next unless visited.add?(current)
    pending.concat(Array(targets.fetch(current)['dependencies']).map { |dependency| dependency['target'] }.compact)
  end
  check(visited.include?('FlareAppleCore'), "#{name} must wait for the Kotlin framework owner")
end
schemes = project.fetch('schemes')
check(schemes.dig('CI-iOS', 'test', 'targets').to_set == (schemes.dig('iOS', 'test', 'targets') + ['TimelineControllerTests']).to_set, 'The CI scheme must compile all original iOS test targets')
check(!File.read(File.join(root, '.github/workflows/android.yml')).include?('enableReleaseBinaryCache=true'), 'Release binary caching must stay disabled')
check(!File.read(File.join(root, 'apple-shared/build.gradle.kts')).include?('-Xauto-cache-from'), 'Release project auto-caching must stay disabled')

puts 'CI contracts passed: dispatch targets, release gates, regression coverage, and one Kotlin framework owner'
