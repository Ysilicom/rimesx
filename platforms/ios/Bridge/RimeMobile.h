#import <Foundation/Foundation.h>
NS_ASSUME_NONNULL_BEGIN
@interface RimeMobile : NSObject
- (nullable instancetype)initWithResources:(NSString *)resources userDirectory:(NSString *)directory;
- (BOOL)selectSchema:(NSString *)schema;
+ (NSDictionary *)deployResources:(NSString *)resources schemaIDs:(NSArray<NSString *> *)schemaIDs;
+ (NSDictionary *)validateResources:(NSString *)resources schemaIDs:(NSArray<NSString *> *)schemaIDs;
- (NSDictionary *)processKey:(int32_t)key;
- (NSDictionary *)selectCandidate:(NSUInteger)index;
- (void)clear;
@property(nonatomic, readonly) NSString *rawInput;
@property(nonatomic, readonly) NSUInteger inputCaret;
@property(nonatomic, readonly) NSString *lastError;
@end
NS_ASSUME_NONNULL_END
