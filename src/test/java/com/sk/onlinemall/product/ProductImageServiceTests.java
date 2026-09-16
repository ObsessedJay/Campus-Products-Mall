package com.sk.onlinemall.product;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.product.mapper.ProductImageMapper;
import com.sk.onlinemall.product.mapper.ProductMapper;
import com.sk.onlinemall.product.model.ProductEntity;
import com.sk.onlinemall.product.service.ProductImageService;
import com.sk.onlinemall.storage.ImageStorageService;
import com.sk.onlinemall.storage.UploadedImageResponse;
import com.sk.onlinemall.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductImageServiceTests {

    /**
     * 验证达到商品图片上限后不会写入对象存储。
     */
    @Test
    void shouldRejectUploadBeforeStorageWhenLimitReached() {
        ProductImageMapper imageMapper = mock(ProductImageMapper.class);
        ProductMapper productMapper = mock(ProductMapper.class);
        ImageStorageService storageService = mock(ImageStorageService.class);
        ProductEntity product = new ProductEntity();
        product.setId(1L);
        when(productMapper.findById(1L)).thenReturn(product);
        when(imageMapper.countByProductId(1L)).thenReturn(8);
        ProductImageService service = new ProductImageService(
                imageMapper, productMapper, mock(UserMapper.class), storageService, 8);

        assertThatThrownBy(() -> service.upload(1L, pngFile(), null, null, "operator"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PRODUCT_IMAGE_LIMIT_EXCEEDED"));
        verify(storageService, never()).upload(any());
    }

    /**
     * 验证数据库写入失败时删除已经上传的对象。
     */
    @Test
    void shouldCompensateObjectWhenDatabaseInsertFails() {
        ProductImageMapper imageMapper = mock(ProductImageMapper.class);
        ProductMapper productMapper = mock(ProductMapper.class);
        ImageStorageService storageService = mock(ImageStorageService.class);
        ProductEntity product = new ProductEntity();
        product.setId(1L);
        when(productMapper.findById(1L)).thenReturn(product);
        when(imageMapper.nextSortOrder(1L)).thenReturn(0);
        when(storageService.upload(any())).thenReturn(new UploadedImageResponse(
                "11111111-1111-1111-1111-111111111111.png", "/api/v1/files/images/test.png", "image/png", 9));
        when(imageMapper.insert(any())).thenThrow(new IllegalStateException("database unavailable"));
        ProductImageService service = new ProductImageService(
                imageMapper, productMapper, mock(UserMapper.class), storageService, 8);

        assertThatThrownBy(() -> service.upload(1L, pngFile(), null, null, "operator"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        verify(storageService).delete("11111111-1111-1111-1111-111111111111.png");
    }

    /**
     * 创建最小合法 PNG 测试文件。
     *
     * @return PNG 测试文件
     */
    private MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "cover.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01});
    }
}
