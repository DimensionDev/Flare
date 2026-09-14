#import <TargetConditionals.h>
#if TARGET_OS_OSX
#import <AppKit/AppKit.h>
typedef NSCollectionViewLayoutAttributes FlareLayoutAttributes;
#else
#import <UIKit/UIKit.h>
typedef UICollectionViewLayoutAttributes FlareLayoutAttributes;
#endif

// These layout hooks are Objective-C category members. Kotlin/Native imports them as
// extensions, so a protocol redeclares their selectors for ordinary Kotlin overrides.
@protocol FlareCollectionLayout <NSObject>
- (void)prepareLayout;
- (CGSize)collectionViewContentSize;
- (NSArray<FlareLayoutAttributes *> * _Nonnull)layoutAttributesForElementsInRect:(CGRect)rect;
- (FlareLayoutAttributes * _Nullable)layoutAttributesForItemAtIndexPath:(NSIndexPath * _Nonnull)indexPath;
- (BOOL)shouldInvalidateLayoutForBoundsChange:(CGRect)newBounds;
@end
