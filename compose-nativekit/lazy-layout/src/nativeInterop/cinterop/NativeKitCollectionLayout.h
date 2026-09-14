#import <TargetConditionals.h>
#if TARGET_OS_OSX
#import <AppKit/AppKit.h>
typedef NSCollectionViewLayoutAttributes NativeKitLayoutAttributes;
#else
#import <UIKit/UIKit.h>
typedef UICollectionViewLayoutAttributes NativeKitLayoutAttributes;
#endif

// These layout hooks are Objective-C category members. Kotlin/Native imports them as
// extensions, so a protocol redeclares their selectors for ordinary Kotlin overrides.
@protocol NativeKitCollectionLayout <NSObject>
- (void)prepareLayout;
- (CGSize)collectionViewContentSize;
- (NSArray<NativeKitLayoutAttributes *> * _Nonnull)layoutAttributesForElementsInRect:(CGRect)rect;
- (NativeKitLayoutAttributes * _Nullable)layoutAttributesForItemAtIndexPath:(NSIndexPath * _Nonnull)indexPath;
- (BOOL)shouldInvalidateLayoutForBoundsChange:(CGRect)newBounds;
@end
