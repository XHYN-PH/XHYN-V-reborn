// Host test for the unmodified upstream GPLALL 2.6.8-2 configuration parser.
// Environment/executable/log adapters only. Does not execute Android or Vulkan.
#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <string>
#include "config.h"
#include "../log/log.h"
#include "../util_env.h"

namespace dxvk::env {
std::string getEnvVar(const char* name) {
  const char* value = std::getenv(name);
  return value ? value : "";
}
std::string getExeName() { return "app_process64"; }
}
namespace dxvk {
void Logger::info(const std::string& text) { std::cout << "INFO " << text << '\n'; }
void Logger::err(const std::string& text) { std::cerr << "ERROR " << text << '\n'; }
}
static int checks = 0;
static void check(bool value, const std::string& name) {
  if (!value) { std::cerr << "FAIL " << name << '\n'; std::exit(1); }
  ++checks;
  std::cout << "PASS " << name << '\n';
}
int main(int argc, char** argv) {
  if (argc != 5) return 2;
  setenv("DXVK_CONFIG_FILE", argv[1], 1);
  setenv("DXVK_CONFIG", "", 1);
  auto config = dxvk::Config::getUserConfig();
  config.logOptions();
  check(config.getOption<int32_t>("dxvk.numCompilerThreads", -1) == 4, "Custom compiler count overrides managed count");
  check(config.getOption<std::string>("dxvk.hud") == "fps,version", "Custom HUD overrides managed HUD in native parser");
  check(config.getOption<int32_t>("dxgi.maxDeviceMemory", -1) == 1536, "Custom reported VRAM cap overrides managed cap");
  check(config.getOption<int32_t>("dxgi.maxSharedMemory", -1) == 4096, "Shared-memory cap parsed");
  check(config.getOption<int32_t>("dxvk.maxMemoryBudget", -1) == 1024, "Allocation budget parsed");
  check(config.getOption<int32_t>("d3d11.samplerAnisotropy", -1) == 16, "Anisotropic filtering parsed");
  check(config.getOption<int32_t>("d3d11.maxTessFactor", -1) == 16, "Tessellation factor parsed");
  check(config.getOption<float>("d3d11.samplerLodBias", -5.f) == 1.f, "Corrected LOD bias parsed");
  check(config.getOption<bool>("d3d11.disableMsaa", false), "MSAA disable parsed");
  check(config.getOption<bool>("dxvk.enableStateCache", false), "State cache parsed");
  check(config.getOption<dxvk::Tristate>("dxvk.useRawSsbo", dxvk::Tristate::Auto) == dxvk::Tristate::True, "Raw-buffer tristate parsed");
  check(config.getOption<bool>("d3d11.relaxedBarriers", false), "Relaxed barriers parsed");
  check(config.getOption<bool>("d3d11.relaxedGraphicsBarriers", false), "Relaxed graphics barriers parsed");
  check(config.getOption<std::string>("d3d11.maxFeatureLevel") == "11_0", "Feature-level cap parsed");
  check(config.getOption<std::string>("d3d9.presentInterval").empty(), "Removed D3D9 setting absent from native config");
  if (!std::getenv("DXVK_CONFIG")) setenv("DXVK_CONFIG", "dxvk.numCompilerThreads = 2", 0);
  check(std::string(std::getenv("DXVK_CONFIG")).empty(), "Engine fallback cannot force two workers");
  setenv("DXVK_CONFIG_FILE", argv[2], 1);
  auto ui = dxvk::Config::getUserConfig();
  check(ui.getOption<int32_t>("dxvk.numCompilerThreads", -1) == 3, "Unified controls write native-readable quoted compiler count");
  check(ui.getOption<int32_t>("dxvk.maxMemoryBudget", -1) == 2048, "Unified controls write native-readable quoted memory budget");
  check(ui.getOption<std::string>("dxvk.hud", "missing").empty(), "Unified HUD off parses as an explicit empty string");
  setenv("DXVK_CONFIG_FILE", argv[3], 1);
  auto scoped = dxvk::Config::getUserConfig();
  check(scoped.getOption<int32_t>("dxvk.numCompilerThreads", -1) == 9, "Native executable section takes disclosed priority over global controls");
  check(scoped.getOption<int32_t>("dxvk.maxMemoryBudget", -1) == 2048, "Unrelated global memory value survives section matching");
  setenv("DXVK_CONFIG_FILE", argv[4], 1);
  auto tuning = dxvk::Config::getUserConfig();
  check(tuning.getOption<std::string>("d3d11.maxFeatureLevel") == "10_1", "DX10.1 control writes a native-readable feature limit");
  check(tuning.getOption<int32_t>("dxgi.maxDeviceMemory", -1) == 3072, "VRAM button value reaches the native parser in MiB");
  check(tuning.getOption<int32_t>("dxgi.maxSharedMemory", -1) == 2048, "Shared-memory button value reaches native parser");
  check(tuning.getOption<int32_t>("dxvk.maxMemoryBudget", -1) == 1536, "Actual allocation budget stays distinct from reported memory");
  check(tuning.getOption<std::string>("d3d11.cachedDynamicResources") == "vi", "Resource-cache flags survive the UI config writer");
  check(!tuning.getOption<bool>("dxvk.enableAsync", true), "Explicit async Off survives native boolean parsing");
  check(tuning.getOption<dxvk::Tristate>("dxvk.useRawSsbo", dxvk::Tristate::False) == dxvk::Tristate::Auto, "Automatic raw storage buffers uses native tristate");
  check(tuning.getOption<dxvk::Tristate>("dxvk.enableMemoryDefrag", dxvk::Tristate::False) == dxvk::Tristate::Auto, "Automatic defragmentation uses native tristate");
  check(tuning.getOption<std::string>("dxvk.framePace") == "low-latency", "GPLALL pacing mode survives native string parsing");
  check(tuning.getOption<bool>("d3d11.enableContextLock", false), "Context-lock compatibility control parsed");
  check(tuning.getOption<bool>("d3d11.floatControls", false), "Float-control correctness option parsed");
  check(tuning.getOption<std::string>("dxvk.hud", "missing").empty(), "HUD Off remains an intentional empty string");
  std::cout << "TOTAL " << checks << " upstream parser checks; host only\n";
}
