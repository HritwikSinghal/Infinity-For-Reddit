{
  description = "Infinity for Reddit - Nix-managed Android build";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs }:
    let
      supportedSystems = [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
      forAllSystems = nixpkgs.lib.genAttrs supportedSystems;

      mkAndroidEnv = system:
        let
          pkgs = import nixpkgs {
            inherit system;
            config = {
              android_sdk.accept_license = true;
              allowUnfree = true;
            };
          };

          androidComposition = pkgs.androidenv.composeAndroidPackages {
            buildToolsVersions = [ "35.0.0" ];
            platformVersions = [ "35" ];
            includeEmulator = false;
            includeNDK = false;
            includeSystemImages = false;
            includeSources = false;
          };
        in
        {
          inherit pkgs;
          jdk = pkgs.jdk17;
          androidSdk = androidComposition.androidsdk;
        };
    in
    {
      devShells = forAllSystems (system:
        let
          env = mkAndroidEnv system;
        in
        {
          default = env.pkgs.mkShell {
            buildInputs = [ env.jdk env.androidSdk ];

            JAVA_HOME = "${env.jdk}";
            ANDROID_HOME = "${env.androidSdk}/libexec/android-sdk";
            ANDROID_SDK_ROOT = "${env.androidSdk}/libexec/android-sdk";

            shellHook = ''
              echo "sdk.dir=$ANDROID_HOME" > local.properties
            '';
          };
        });

      packages = forAllSystems (system:
        let
          env = mkAndroidEnv system;
          mkBuildScript = name: task: env.pkgs.writeShellApplication {
            inherit name;
            runtimeInputs = [ env.jdk env.androidSdk ];
            text = ''
              export JAVA_HOME="${env.jdk}"
              export ANDROID_HOME="${env.androidSdk}/libexec/android-sdk"
              export ANDROID_SDK_ROOT="$ANDROID_HOME"
              echo "sdk.dir=$ANDROID_HOME" > local.properties
              ./gradlew ${task} "$@"
            '';
          };
        in
        {
          build-debug = mkBuildScript "build-debug" "assembleDebug";
          build-release = mkBuildScript "build-release" "assembleRelease";
          build-minified = mkBuildScript "build-minified" "assembleMinifiedRelease";
        });
    };
}
