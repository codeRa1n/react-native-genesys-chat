#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RCT_EXTERN_MODULE(GenesysChat, RCTEventEmitter)

RCT_EXTERN_METHOD(startChat:(NSString *)deploymentId
                  domain:(NSString *)domain
                  attributes:(NSDictionary *)attributes
                  logging:(BOOL)logging)

RCT_EXTERN_METHOD(setupMessengerTransport:(NSString *)deploymentId
                  domain:(NSString *)domain
                  attributes:(NSDictionary *)attributes
                  logging:(BOOL)logging)

RCT_EXTERN_METHOD(getConversation:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(sendMessage:(NSString *)query
                  attributes:(NSDictionary *)attributes)
RCT_EXTERN_METHOD(clearHistory)
RCT_EXTERN_METHOD(disconnectAndCleanup)


+ (BOOL)requiresMainQueueSetup
{
  return NO;
}

@end
