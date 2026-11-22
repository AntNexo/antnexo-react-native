#import "AntNexo.h"

#if __has_include(<AntNexoReactNative/AntNexoReactNative-Swift.h>)
#import <AntNexoReactNative/AntNexoReactNative-Swift.h>
#else
#import "AntNexoReactNative-Swift.h"
#endif

// Forwards the TurboModule's calls to AntNexoModuleImpl (Swift), which calls the vendored Swift SDK.
@implementation AntNexo {
  AntNexoModuleImpl *_impl;
}

- (instancetype)init
{
  if (self = [super init]) {
    _impl = [AntNexoModuleImpl new];
    __weak AntNexo *weakSelf = self;
    _impl.emit = ^(NSDictionary<NSString *, id> *event) {
      [weakSelf emitOnEvent:event];
    };
  }
  return self;
}

+ (BOOL)requiresMainQueueSetup
{
  return NO;
}

- (void)setWrapper:(NSString *)sdk
{
  [_impl setWrapper:sdk];
}

static NSArray<NSString *> *strings(std::optional<facebook::react::LazyVector<NSString *>> v)
{
  if (!v) return nil;
  NSMutableArray<NSString *> *out = [NSMutableArray new];
  for (NSString *s : *v) {
    if (s) [out addObject:s];
  }
  return out;
}

- (void)configure:(NSString *)host
        workspace:(NSString *)workspace
          options:(JS::NativeAntNexo::NativeOptions &)options
          resolve:(RCTPromiseResolveBlock)resolve
           reject:(RCTPromiseRejectBlock)reject
{
  NSMutableDictionary<NSString *, id> *o = [NSMutableDictionary new];
  if (options.tab()) o[@"tab"] = options.tab();
  if (NSArray *tabs = strings(options.tabs())) o[@"tabs"] = tabs;
  if (NSArray *boards = strings(options.boards())) o[@"boards"] = boards;
  if (options.theme()) o[@"theme"] = options.theme();
  if (options.color()) o[@"color"] = options.color();
  if (options.prompts()) o[@"prompts"] = @(*options.prompts());
  if (options.whatsNew()) o[@"whatsNew"] = options.whatsNew();
  if (options.language()) o[@"language"] = options.language();
  [_impl configure:host
         workspace:workspace
           options:o
           resolve:^(id value) { resolve(value); }
            reject:^(NSString *code, NSString *message, NSDictionary *info) {
              reject(code, message, [NSError errorWithDomain:@"AntNexo" code:0 userInfo:info]);
            }];
}

- (void)show:(NSString *)tab
  screenshot:(BOOL)screenshot
       token:(NSString *)token
     resolve:(RCTPromiseResolveBlock)resolve
      reject:(RCTPromiseRejectBlock)reject
{
  [_impl show:tab screenshot:screenshot token:token resolve:^(id value) { resolve(value); }];
}

- (void)close
{
  [_impl close];
}

- (void)unreadCount:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  [_impl unreadCount:^(id value) { resolve(value); }
              reject:^(NSString *code, NSString *message, NSDictionary *info) {
                reject(code, message, [NSError errorWithDomain:@"AntNexo" code:0 userInfo:info]);
              }];
}

- (void)logout:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  [_impl logout:^(id value) { resolve(value); }];
}

- (void)invalidate
{
  [_impl invalidate];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
  return std::make_shared<facebook::react::NativeAntNexoSpecJSI>(params);
}

+ (NSString *)moduleName
{
  return @"AntNexo";
}

@end
