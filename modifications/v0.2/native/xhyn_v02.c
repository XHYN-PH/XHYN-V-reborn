#define _GNU_SOURCE
#define VK_NO_PROTOTYPES
#define VK_USE_PLATFORM_ANDROID_KHR
#include <jni.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <vulkan/vulkan.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <time.h>
#include <unistd.h>
#include <unwind.h>
#include "probe_spv.h"

static char private_root[PATH_MAX - 32];
static char session_id[160];
static uintptr_t installed_engine;

struct trace_output { int fd; unsigned index; };
static _Unwind_Reason_Code trace_frame(struct _Unwind_Context *context, void *opaque) {
    struct trace_output *out = opaque;
    if (out->index >= 32) return _URC_END_OF_STACK;
    uintptr_t pc = (uintptr_t)_Unwind_GetIP(context);
    Dl_info info = {0};
    dladdr((void *)pc, &info);
    const char *name = info.dli_fname ? strrchr(info.dli_fname, '/') : NULL;
    name = name ? name + 1 : info.dli_fname ? info.dli_fname : "unknown";
    char line[512];
    int length = snprintf(line, sizeof(line), "frame=%u module=%s pc=0x%lx relative=0x%lx\n",
                          out->index++, name, (unsigned long)pc,
                          (unsigned long)(pc - (uintptr_t)info.dli_fbase));
    if (length > 0) write(out->fd, line, (size_t)length < sizeof(line) ? (size_t)length : sizeof(line) - 1);
    return _URC_NO_REASON;
}

__attribute__((noreturn, noinline)) static void controlled_stop(uint32_t code) {
    char file[PATH_MAX], stage[PATH_MAX];
    snprintf(file, sizeof(file), "%s/xhyn-native-error.txt", private_root);
    snprintf(stage, sizeof(stage), "%s/xhyn-native-error.part", private_root);
    const char *reason = code == 0x9cf7bd06u ? "Graphics device initialization or device-loss failure"
                       : code == 0xe79d84ffu ? "The selected renderer could not create a compatible graphics device"
                       : code == 0x8392273du ? "Invalid resource mapping during loading"
                       : "The engine reported an unrecoverable error";
    int fd = open(stage, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    if (fd >= 0) {
        struct timespec now;
        clock_gettime(CLOCK_REALTIME, &now);
        char header[1024];
        int size = snprintf(header, sizeof(header),
                "XHYN_NATIVE_ERROR_V1\nsession=%s\npid=%d\ntime_ms=%lld\nerror_code=0x%08x\nreason=%s\nexit_status=73\n",
                session_id, getpid(), (long long)now.tv_sec * 1000 + now.tv_nsec / 1000000, code, reason);
        if (size > 0) write(fd, header, (size_t)size < sizeof(header) ? (size_t)size : sizeof(header) - 1);
        struct trace_output output = {fd, 0};
        _Unwind_Backtrace(trace_frame, &output);
        fsync(fd);
        close(fd);
        rename(stage, file);
    }
    __android_log_print(ANDROID_LOG_ERROR, "XHYN V", "%s; code=0x%08x; session=%s", reason, code, session_id);
    _exit(73);
}

JNIEXPORT jboolean JNICALL Java_com_gtavsource_android_XhynV02Native_install(JNIEnv *env, jclass type, jstring directory, jstring session) {
    (void)type;
    if (!directory || !session) return JNI_FALSE;
    const char *root = (*env)->GetStringUTFChars(env, directory, NULL);
    const char *id = (*env)->GetStringUTFChars(env, session, NULL);
    if (!root || !id) {
        if (root) (*env)->ReleaseStringUTFChars(env, directory, root);
        if (id) (*env)->ReleaseStringUTFChars(env, session, id);
        return JNI_FALSE;
    }
    int valid = strlen(root) < sizeof(private_root) - 32 && strlen(id) < sizeof(session_id);
    if (valid) { strcpy(private_root, root); strcpy(session_id, id); }
    (*env)->ReleaseStringUTFChars(env, directory, root);
    (*env)->ReleaseStringUTFChars(env, session, id);
    if (!valid) return JNI_FALSE;
    void *handle = dlopen("libgtav.so", RTLD_NOW | RTLD_NOLOAD);
    if (!handle) return JNI_FALSE;
    void *entry = dlsym(handle, "SDL_main");
    Dl_info info = {0};
    if (!entry || !dladdr(entry, &info)) { dlclose(handle); return JNI_FALSE; }
    uintptr_t base = (uintptr_t)info.dli_fbase;
    if (installed_engine == base) { dlclose(handle); return JNI_TRUE; }
    unsigned char *target = (unsigned char *)(base + 0x2a3c784);
    static const unsigned char expected[16] = {0xfd,0x7b,0xbf,0xa9,0xfd,0x03,0x00,0x91,0xe0,0x03,0x1f,0xaa,0xd0,0x56,0x22,0x94};
    if (memcmp(target, expected, sizeof(expected))) { dlclose(handle); return JNI_FALSE; }
    long page = sysconf(_SC_PAGESIZE);
    if (page <= 0 || (page & (page - 1))) { dlclose(handle); return JNI_FALSE; }
    uintptr_t first = (uintptr_t)target & ~((uintptr_t)page - 1);
    size_t length = (((uintptr_t)target + 16 + page - 1) & ~((uintptr_t)page - 1)) - first;
    if (mprotect((void *)first, length, PROT_READ | PROT_WRITE | PROT_EXEC)) { dlclose(handle); return JNI_FALSE; }
    uint32_t jump[4] = {0x58000050u, 0xd61f0200u, 0, 0};
    uintptr_t destination = (uintptr_t)controlled_stop;
    memcpy(&jump[2], &destination, sizeof(destination));
    memcpy(target, jump, sizeof(jump));
    __builtin___clear_cache((char *)target, (char *)target + sizeof(jump));
    int protected = mprotect((void *)first, length, PROT_READ | PROT_EXEC) == 0;
    if (!protected) {
        memcpy(target, expected, sizeof(expected));
        __builtin___clear_cache((char *)target, (char *)target + sizeof(expected));
        mprotect((void *)first, length, PROT_READ | PROT_EXEC);
    } else installed_engine = base;
    dlclose(handle);
    return protected ? JNI_TRUE : JNI_FALSE;
}

static void escaped(char *out, size_t capacity, const char *text) {
    size_t count = 0;
    for (const unsigned char *p = (const unsigned char *)text; *p && count + 7 < capacity; ++p) {
        if (*p == '"' || *p == '\\') { out[count++] = '\\'; out[count++] = (char)*p; }
        else if (*p < 32) { out[count++] = ' '; }
        else out[count++] = (char)*p;
    }
    out[count] = 0;
}

#define INSTANCE_FN(name) PFN_##name name = (PFN_##name)get(instance, #name)
#define DEVICE_FN(name) PFN_##name name = (PFN_##name)get_device(device, #name)
#define REQUIRED(name) do { if (!(name)) { snprintf(error, sizeof(error), "Missing Vulkan command: %s", #name); goto cleanup; } } while (0)
#define VK_CHECK(call) do { result = (call); if (result != VK_SUCCESS) { snprintf(error, sizeof(error), "%s returned %d", #call, result); goto cleanup; } } while (0)

static jstring probe(JNIEnv *env, jobject java_surface) {
    char error[256] = {0}, status[32] = "FAILED";
    void *library = NULL;
    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice physical = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    ANativeWindow *window = NULL;
    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    VkRenderPass render_pass = VK_NULL_HANDLE;
    VkPipelineLayout layout = VK_NULL_HANDLE;
    VkPipeline pipeline = VK_NULL_HANDLE;
    VkShaderModule vertex = VK_NULL_HANDLE, fragment = VK_NULL_HANDLE;
    VkCommandPool command_pool = VK_NULL_HANDLE;
    VkFence fence = VK_NULL_HANDLE;
    VkSemaphore acquired[2] = {0}, rendered[2] = {0};
    VkImage images[16] = {0};
    VkImageView views[16] = {0};
    VkFramebuffer frames[16] = {0};
    VkCommandBuffer commands[16] = {0};
    uint32_t image_count = 0, frames_presented = 0;
    VkPhysicalDeviceProperties properties = {0};
    VkPhysicalDeviceFeatures features = {0};
    VkPhysicalDeviceDriverProperties driver = {.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES};
    VkPhysicalDeviceExtendedDynamicState3FeaturesEXT dynamic = {.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTENDED_DYNAMIC_STATE_3_FEATURES_EXT};
    VkResult result = VK_SUCCESS;
    PFN_vkGetInstanceProcAddr get = NULL;
    PFN_vkGetDeviceProcAddr get_device = NULL;
    PFN_vkDestroyInstance destroy_instance = NULL;
    PFN_vkDestroySurfaceKHR destroy_surface = NULL;
    PFN_vkDestroyDevice destroy_device = NULL;

    library = dlopen("libgtav_vulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (!library) { snprintf(error, sizeof(error), "The selected Vulkan loader could not be opened"); goto cleanup; }
    get = (PFN_vkGetInstanceProcAddr)dlsym(library, "vkGetInstanceProcAddr");
    REQUIRED(get);
    PFN_vkCreateInstance create_instance = (PFN_vkCreateInstance)get(VK_NULL_HANDLE, "vkCreateInstance");
    REQUIRED(create_instance);
    const char *instance_extensions[] = {VK_KHR_SURFACE_EXTENSION_NAME, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME};
    VkApplicationInfo application = {.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO, .pApplicationName = "XHYN V driver readiness", .apiVersion = VK_API_VERSION_1_1};
    VkInstanceCreateInfo instance_info = {.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, .pApplicationInfo = &application,
        .enabledExtensionCount = java_surface ? 2 : 0, .ppEnabledExtensionNames = instance_extensions};
    VK_CHECK(create_instance(&instance_info, NULL, &instance));
    destroy_instance = (PFN_vkDestroyInstance)get(instance, "vkDestroyInstance");
    INSTANCE_FN(vkEnumeratePhysicalDevices); REQUIRED(vkEnumeratePhysicalDevices);
    INSTANCE_FN(vkGetPhysicalDeviceProperties); REQUIRED(vkGetPhysicalDeviceProperties);
    INSTANCE_FN(vkGetPhysicalDeviceFeatures); REQUIRED(vkGetPhysicalDeviceFeatures);
    uint32_t physical_count = 1;
    result = vkEnumeratePhysicalDevices(instance, &physical_count, &physical);
    if ((result != VK_SUCCESS && result != VK_INCOMPLETE) || !physical_count || !physical) {
        snprintf(error, sizeof(error), "The selected driver exposes no Vulkan GPU (%d)", result); goto cleanup;
    }
    vkGetPhysicalDeviceProperties(physical, &properties);
    vkGetPhysicalDeviceFeatures(physical, &features);
    INSTANCE_FN(vkGetPhysicalDeviceProperties2);
    if (vkGetPhysicalDeviceProperties2) {
        VkPhysicalDeviceProperties2 props = {.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2, .pNext = &driver};
        vkGetPhysicalDeviceProperties2(physical, &props);
    }
    INSTANCE_FN(vkGetPhysicalDeviceFeatures2);
    if (vkGetPhysicalDeviceFeatures2) {
        VkPhysicalDeviceFeatures2 queried = {.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2, .pNext = &dynamic};
        vkGetPhysicalDeviceFeatures2(physical, &queried);
    }
    if (!java_surface) { strcpy(status, "IDENTIFIED"); goto cleanup; }

    INSTANCE_FN(vkCreateAndroidSurfaceKHR); REQUIRED(vkCreateAndroidSurfaceKHR);
    destroy_surface = (PFN_vkDestroySurfaceKHR)get(instance, "vkDestroySurfaceKHR");
    window = ANativeWindow_fromSurface(env, java_surface);
    if (!window) { snprintf(error, sizeof(error), "The test surface is unavailable"); goto cleanup; }
    VkAndroidSurfaceCreateInfoKHR surface_info = {.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR, .window = window};
    VK_CHECK(vkCreateAndroidSurfaceKHR(instance, &surface_info, NULL, &surface));
    INSTANCE_FN(vkGetPhysicalDeviceQueueFamilyProperties); REQUIRED(vkGetPhysicalDeviceQueueFamilyProperties);
    INSTANCE_FN(vkGetPhysicalDeviceSurfaceSupportKHR); REQUIRED(vkGetPhysicalDeviceSurfaceSupportKHR);
    INSTANCE_FN(vkGetPhysicalDeviceSurfaceCapabilitiesKHR); REQUIRED(vkGetPhysicalDeviceSurfaceCapabilitiesKHR);
    INSTANCE_FN(vkGetPhysicalDeviceSurfaceFormatsKHR); REQUIRED(vkGetPhysicalDeviceSurfaceFormatsKHR);
    INSTANCE_FN(vkCreateDevice); REQUIRED(vkCreateDevice);
    get_device = (PFN_vkGetDeviceProcAddr)get(instance, "vkGetDeviceProcAddr"); REQUIRED(get_device);
    uint32_t queue_count = 0, queue_family = UINT32_MAX;
    vkGetPhysicalDeviceQueueFamilyProperties(physical, &queue_count, NULL);
    if (queue_count > 64 || !queue_count) { snprintf(error, sizeof(error), "Unsupported queue-family count"); goto cleanup; }
    VkQueueFamilyProperties queues[64];
    vkGetPhysicalDeviceQueueFamilyProperties(physical, &queue_count, queues);
    for (uint32_t index = 0; index < queue_count; index++) {
        VkBool32 present = VK_FALSE;
        if ((queues[index].queueFlags & VK_QUEUE_GRAPHICS_BIT) && vkGetPhysicalDeviceSurfaceSupportKHR(physical, index, surface, &present) == VK_SUCCESS && present) { queue_family = index; break; }
    }
    if (queue_family == UINT32_MAX) { snprintf(error, sizeof(error), "No graphics queue can present to Android"); goto cleanup; }
    float priority = 1.0f;
    VkDeviceQueueCreateInfo queue_info = {.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO, .queueFamilyIndex = queue_family, .queueCount = 1, .pQueuePriorities = &priority};
    const char *device_extensions[] = {VK_KHR_SWAPCHAIN_EXTENSION_NAME};
    VkDeviceCreateInfo device_info = {.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO, .queueCreateInfoCount = 1, .pQueueCreateInfos = &queue_info,
        .enabledExtensionCount = 1, .ppEnabledExtensionNames = device_extensions};
    VK_CHECK(vkCreateDevice(physical, &device_info, NULL, &device));
    destroy_device = (PFN_vkDestroyDevice)get_device(device, "vkDestroyDevice");
    DEVICE_FN(vkGetDeviceQueue); REQUIRED(vkGetDeviceQueue);
    DEVICE_FN(vkCreateSwapchainKHR); REQUIRED(vkCreateSwapchainKHR);
    DEVICE_FN(vkGetSwapchainImagesKHR); REQUIRED(vkGetSwapchainImagesKHR);
    DEVICE_FN(vkCreateImageView); REQUIRED(vkCreateImageView);
    DEVICE_FN(vkCreateRenderPass); REQUIRED(vkCreateRenderPass);
    DEVICE_FN(vkCreateFramebuffer); REQUIRED(vkCreateFramebuffer);
    DEVICE_FN(vkCreateShaderModule); REQUIRED(vkCreateShaderModule);
    DEVICE_FN(vkCreatePipelineLayout); REQUIRED(vkCreatePipelineLayout);
    DEVICE_FN(vkCreateGraphicsPipelines); REQUIRED(vkCreateGraphicsPipelines);
    DEVICE_FN(vkCreateCommandPool); REQUIRED(vkCreateCommandPool);
    DEVICE_FN(vkAllocateCommandBuffers); REQUIRED(vkAllocateCommandBuffers);
    DEVICE_FN(vkBeginCommandBuffer); REQUIRED(vkBeginCommandBuffer);
    DEVICE_FN(vkEndCommandBuffer); REQUIRED(vkEndCommandBuffer);
    DEVICE_FN(vkCmdBeginRenderPass); REQUIRED(vkCmdBeginRenderPass);
    DEVICE_FN(vkCmdEndRenderPass); REQUIRED(vkCmdEndRenderPass);
    DEVICE_FN(vkCmdBindPipeline); REQUIRED(vkCmdBindPipeline);
    DEVICE_FN(vkCmdDraw); REQUIRED(vkCmdDraw);
    DEVICE_FN(vkCreateFence); REQUIRED(vkCreateFence);
    DEVICE_FN(vkCreateSemaphore); REQUIRED(vkCreateSemaphore);
    DEVICE_FN(vkAcquireNextImageKHR); REQUIRED(vkAcquireNextImageKHR);
    DEVICE_FN(vkQueueSubmit); REQUIRED(vkQueueSubmit);
    DEVICE_FN(vkQueuePresentKHR); REQUIRED(vkQueuePresentKHR);
    DEVICE_FN(vkWaitForFences); REQUIRED(vkWaitForFences);
    DEVICE_FN(vkResetFences); REQUIRED(vkResetFences);
    VkQueue queue = VK_NULL_HANDLE;
    vkGetDeviceQueue(device, queue_family, 0, &queue);
    VkSurfaceCapabilitiesKHR capabilities;
    VK_CHECK(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physical, surface, &capabilities));
    uint32_t format_count = 0;
    VK_CHECK(vkGetPhysicalDeviceSurfaceFormatsKHR(physical, surface, &format_count, NULL));
    if (!format_count || format_count > 128) { snprintf(error, sizeof(error), "No usable surface format"); goto cleanup; }
    VkSurfaceFormatKHR formats[128];
    VK_CHECK(vkGetPhysicalDeviceSurfaceFormatsKHR(physical, surface, &format_count, formats));
    VkSurfaceFormatKHR format = formats[0];
    for (uint32_t index = 0; index < format_count; index++) if (formats[index].format == VK_FORMAT_R8G8B8A8_UNORM || formats[index].format == VK_FORMAT_B8G8R8A8_UNORM) { format = formats[index]; break; }
    if (format.format == VK_FORMAT_UNDEFINED) format.format = VK_FORMAT_R8G8B8A8_UNORM;
    VkExtent2D extent = capabilities.currentExtent;
    if (extent.width == UINT32_MAX) {
        extent.width = (uint32_t)ANativeWindow_getWidth(window);
        extent.height = (uint32_t)ANativeWindow_getHeight(window);
        if (extent.width < capabilities.minImageExtent.width) extent.width = capabilities.minImageExtent.width;
        if (extent.height < capabilities.minImageExtent.height) extent.height = capabilities.minImageExtent.height;
        if (extent.width > capabilities.maxImageExtent.width) extent.width = capabilities.maxImageExtent.width;
        if (extent.height > capabilities.maxImageExtent.height) extent.height = capabilities.maxImageExtent.height;
    }
    if (!extent.width || !extent.height || extent.width > 4096 || extent.height > 4096) { snprintf(error, sizeof(error), "The test surface has an invalid extent"); goto cleanup; }
    uint32_t wanted_images = capabilities.minImageCount + 1;
    if (capabilities.maxImageCount && wanted_images > capabilities.maxImageCount) wanted_images = capabilities.maxImageCount;
    VkCompositeAlphaFlagBitsKHR alpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    if (!(capabilities.supportedCompositeAlpha & alpha)) alpha = (VkCompositeAlphaFlagBitsKHR)(capabilities.supportedCompositeAlpha & (0u - capabilities.supportedCompositeAlpha));
    VkSwapchainCreateInfoKHR swapchain_info = {.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR, .surface = surface,
        .minImageCount = wanted_images, .imageFormat = format.format, .imageColorSpace = format.colorSpace,
        .imageExtent = extent, .imageArrayLayers = 1, .imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT,
        .imageSharingMode = VK_SHARING_MODE_EXCLUSIVE, .preTransform = capabilities.currentTransform,
        .compositeAlpha = alpha, .presentMode = VK_PRESENT_MODE_FIFO_KHR, .clipped = VK_TRUE};
    VK_CHECK(vkCreateSwapchainKHR(device, &swapchain_info, NULL, &swapchain));
    VK_CHECK(vkGetSwapchainImagesKHR(device, swapchain, &image_count, NULL));
    if (!image_count || image_count > 16) { image_count = 0; snprintf(error, sizeof(error), "Unsupported swapchain image count"); goto cleanup; }
    VK_CHECK(vkGetSwapchainImagesKHR(device, swapchain, &image_count, images));
    VkAttachmentDescription attachment = {.format = format.format, .samples = VK_SAMPLE_COUNT_1_BIT, .loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR,
        .storeOp = VK_ATTACHMENT_STORE_OP_STORE, .stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE, .stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE,
        .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED, .finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR};
    VkAttachmentReference color = {.attachment = 0, .layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription subpass = {.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS, .colorAttachmentCount = 1, .pColorAttachments = &color};
    VkSubpassDependency dependency = {.srcSubpass = VK_SUBPASS_EXTERNAL, .dstSubpass = 0,
        .srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, .dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
        .dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT};
    VkRenderPassCreateInfo pass_info = {.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO, .attachmentCount = 1, .pAttachments = &attachment,
        .subpassCount = 1, .pSubpasses = &subpass, .dependencyCount = 1, .pDependencies = &dependency};
    VK_CHECK(vkCreateRenderPass(device, &pass_info, NULL, &render_pass));
    for (uint32_t index = 0; index < image_count; index++) {
        VkImageViewCreateInfo view_info = {.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO, .image = images[index], .viewType = VK_IMAGE_VIEW_TYPE_2D,
            .format = format.format, .subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1}};
        VK_CHECK(vkCreateImageView(device, &view_info, NULL, &views[index]));
        VkFramebufferCreateInfo frame_info = {.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO, .renderPass = render_pass,
            .attachmentCount = 1, .pAttachments = &views[index], .width = extent.width, .height = extent.height, .layers = 1};
        VK_CHECK(vkCreateFramebuffer(device, &frame_info, NULL, &frames[index]));
    }
    VkShaderModuleCreateInfo shader_info = {.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO, .codeSize = sizeof(probe_vertex), .pCode = probe_vertex};
    VK_CHECK(vkCreateShaderModule(device, &shader_info, NULL, &vertex));
    shader_info.codeSize = sizeof(probe_fragment); shader_info.pCode = probe_fragment;
    VK_CHECK(vkCreateShaderModule(device, &shader_info, NULL, &fragment));
    VkPipelineShaderStageCreateInfo stages[2] = {{.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO, .stage = VK_SHADER_STAGE_VERTEX_BIT, .module = vertex, .pName = "main"},
                                               {.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO, .stage = VK_SHADER_STAGE_FRAGMENT_BIT, .module = fragment, .pName = "main"}};
    VkPipelineVertexInputStateCreateInfo input = {.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};
    VkPipelineInputAssemblyStateCreateInfo assembly = {.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO, .topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST};
    VkViewport viewport = {0, 0, (float)extent.width, (float)extent.height, 0, 1};
    VkRect2D scissor = {{0, 0}, extent};
    VkPipelineViewportStateCreateInfo viewport_info = {.sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO, .viewportCount = 1, .pViewports = &viewport, .scissorCount = 1, .pScissors = &scissor};
    VkPipelineRasterizationStateCreateInfo raster = {.sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO, .polygonMode = VK_POLYGON_MODE_FILL, .cullMode = VK_CULL_MODE_NONE, .frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE, .lineWidth = 1};
    VkPipelineMultisampleStateCreateInfo samples = {.sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO, .rasterizationSamples = VK_SAMPLE_COUNT_1_BIT};
    VkPipelineColorBlendAttachmentState blend_attachment = {.colorWriteMask = 15};
    VkPipelineColorBlendStateCreateInfo blend = {.sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO, .attachmentCount = 1, .pAttachments = &blend_attachment};
    VkPipelineLayoutCreateInfo layout_info = {.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
    VK_CHECK(vkCreatePipelineLayout(device, &layout_info, NULL, &layout));
    VkGraphicsPipelineCreateInfo pipeline_info = {.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO, .stageCount = 2, .pStages = stages,
        .pVertexInputState = &input, .pInputAssemblyState = &assembly, .pViewportState = &viewport_info, .pRasterizationState = &raster,
        .pMultisampleState = &samples, .pColorBlendState = &blend, .layout = layout, .renderPass = render_pass};
    VK_CHECK(vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &pipeline_info, NULL, &pipeline));
    VkCommandPoolCreateInfo pool_info = {.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO, .queueFamilyIndex = queue_family};
    VK_CHECK(vkCreateCommandPool(device, &pool_info, NULL, &command_pool));
    VkCommandBufferAllocateInfo allocate = {.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO, .commandPool = command_pool, .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY, .commandBufferCount = image_count};
    VK_CHECK(vkAllocateCommandBuffers(device, &allocate, commands));
    for (uint32_t index = 0; index < image_count; index++) {
        VkCommandBufferBeginInfo begin = {.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
        VK_CHECK(vkBeginCommandBuffer(commands[index], &begin));
        VkClearValue clear = {.color = {{0.01f, 0.01f, 0.02f, 1.0f}}};
        VkRenderPassBeginInfo pass = {.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO, .renderPass = render_pass, .framebuffer = frames[index],
            .renderArea = {{0, 0}, extent}, .clearValueCount = 1, .pClearValues = &clear};
        vkCmdBeginRenderPass(commands[index], &pass, VK_SUBPASS_CONTENTS_INLINE);
        vkCmdBindPipeline(commands[index], VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        vkCmdDraw(commands[index], 3, 1, 0, 0);
        vkCmdEndRenderPass(commands[index]);
        VK_CHECK(vkEndCommandBuffer(commands[index]));
    }
    VkFenceCreateInfo fence_info = {.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
    VkSemaphoreCreateInfo semaphore_info = {.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
    VK_CHECK(vkCreateFence(device, &fence_info, NULL, &fence));
    for (unsigned frame = 0; frame < 2; frame++) {
        VK_CHECK(vkCreateSemaphore(device, &semaphore_info, NULL, &acquired[frame]));
        VK_CHECK(vkCreateSemaphore(device, &semaphore_info, NULL, &rendered[frame]));
    }
    for (unsigned frame = 0; frame < 2; frame++) {
        uint32_t index = 0;
        result = vkAcquireNextImageKHR(device, swapchain, 2000000000ULL, acquired[frame], VK_NULL_HANDLE, &index);
        if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) { snprintf(error, sizeof(error), "Image acquisition returned %d", result); goto cleanup; }
        if (index >= image_count) { snprintf(error, sizeof(error), "Invalid acquired image index"); goto cleanup; }
        VkPipelineStageFlags wait_stage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        VkSubmitInfo submit = {.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO, .waitSemaphoreCount = 1, .pWaitSemaphores = &acquired[frame],
            .pWaitDstStageMask = &wait_stage, .commandBufferCount = 1, .pCommandBuffers = &commands[index], .signalSemaphoreCount = 1, .pSignalSemaphores = &rendered[frame]};
        VK_CHECK(vkQueueSubmit(queue, 1, &submit, fence));
        VkPresentInfoKHR present = {.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR, .waitSemaphoreCount = 1, .pWaitSemaphores = &rendered[frame],
            .swapchainCount = 1, .pSwapchains = &swapchain, .pImageIndices = &index};
        result = vkQueuePresentKHR(queue, &present);
        if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) { snprintf(error, sizeof(error), "Presentation returned %d", result); goto cleanup; }
        VK_CHECK(vkWaitForFences(device, 1, &fence, VK_TRUE, 2000000000ULL));
        VK_CHECK(vkResetFences(device, 1, &fence));
        frames_presented++;
    }
    strcpy(status, "PASS");

cleanup:
    if (device && get_device) {
        DEVICE_FN(vkDeviceWaitIdle);
        if (vkDeviceWaitIdle) vkDeviceWaitIdle(device);
        DEVICE_FN(vkDestroyFence); if (fence && vkDestroyFence) vkDestroyFence(device, fence, NULL);
        DEVICE_FN(vkDestroySemaphore); if (vkDestroySemaphore) for (unsigned frame = 0; frame < 2; frame++) { if (acquired[frame]) vkDestroySemaphore(device, acquired[frame], NULL); if (rendered[frame]) vkDestroySemaphore(device, rendered[frame], NULL); }
        DEVICE_FN(vkDestroyCommandPool); if (command_pool && vkDestroyCommandPool) vkDestroyCommandPool(device, command_pool, NULL);
        DEVICE_FN(vkDestroyPipeline); if (pipeline && vkDestroyPipeline) vkDestroyPipeline(device, pipeline, NULL);
        DEVICE_FN(vkDestroyPipelineLayout); if (layout && vkDestroyPipelineLayout) vkDestroyPipelineLayout(device, layout, NULL);
        DEVICE_FN(vkDestroyShaderModule); if (vkDestroyShaderModule) { if (vertex) vkDestroyShaderModule(device, vertex, NULL); if (fragment) vkDestroyShaderModule(device, fragment, NULL); }
        DEVICE_FN(vkDestroyFramebuffer); DEVICE_FN(vkDestroyImageView);
        for (uint32_t index = 0; index < image_count; index++) { if (frames[index] && vkDestroyFramebuffer) vkDestroyFramebuffer(device, frames[index], NULL); if (views[index] && vkDestroyImageView) vkDestroyImageView(device, views[index], NULL); }
        DEVICE_FN(vkDestroyRenderPass); if (render_pass && vkDestroyRenderPass) vkDestroyRenderPass(device, render_pass, NULL);
        DEVICE_FN(vkDestroySwapchainKHR); if (swapchain && vkDestroySwapchainKHR) vkDestroySwapchainKHR(device, swapchain, NULL);
        if (destroy_device) destroy_device(device, NULL);
    }
    if (surface && destroy_surface) destroy_surface(instance, surface, NULL);
    if (window) ANativeWindow_release(window);
    if (instance && destroy_instance) destroy_instance(instance, NULL);
    if (library) dlclose(library);
    char gpu_name[768], driver_name[768], driver_info[768], failure[768], json[4096];
    escaped(gpu_name, sizeof(gpu_name), properties.deviceName);
    escaped(driver_name, sizeof(driver_name), driver.driverName);
    escaped(driver_info, sizeof(driver_info), driver.driverInfo);
    escaped(failure, sizeof(failure), error);
    snprintf(json, sizeof(json),
        "{\"status\":\"%s\",\"device\":\"%s\",\"vendorId\":%u,\"deviceId\":%u,\"driverId\":%u,\"driverName\":\"%s\",\"driverInfo\":\"%s\",\"apiVersion\":%u,\"dualSrcBlend\":%s,\"reportedBcCompression\":%s,\"dynamicSampleLocations\":%s,\"presentedFrames\":%u,\"error\":\"%s\",\"scope\":\"Basic Vulkan triangle and Android presentation; no game or texture correctness validation\"}",
        status, gpu_name, properties.vendorID, properties.deviceID, driver.driverID, driver_name, driver_info, properties.apiVersion,
        features.dualSrcBlend ? "true" : "false", features.textureCompressionBC ? "true" : "false",
        dynamic.extendedDynamicState3SampleLocationsEnable ? "true" : "false", frames_presented, failure);
    return (*env)->NewStringUTF(env, json);
}

JNIEXPORT jstring JNICALL Java_com_gtavsource_android_XhynV02Native_identity(JNIEnv *env, jclass type) {
    (void)type; return probe(env, NULL);
}
JNIEXPORT jstring JNICALL Java_com_gtavsource_android_XhynV02Native_probe(JNIEnv *env, jclass type, jobject surface) {
    (void)type; return probe(env, surface);
}
