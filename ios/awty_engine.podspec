#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint awty_engine.podspec` to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'awty_engine'
  s.version          = '1.0.0'
  s.summary          = 'A reliable, decoupled step tracking engine for Flutter applications.'
  s.description      = <<-DESC
A simple, reliable, and decoupled step tracking engine for Flutter applications. AWTY provides pure step counting and goal notification services, allowing you to focus on building your game's UI and logic.
                       DESC
  s.homepage         = 'https://gamesafoot.co'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'Games Afoot' => 'info@gamesafoot.co' }
  s.source           = { :path => '.' }
  s.source_files     = 'Classes/**/*'
  s.dependency 'Flutter'
  s.platform = :ios, '14.0'

  # Flutter.framework does not contain a i386 slice.
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
  s.swift_version = '5.0'
end
