import { afterEach, describe, expect, it, vi } from 'vitest'
import { createCategory, createProduct, deleteCategory, deleteProduct, deleteProductImage, getManagedCategories, getManagedProducts, putProductOnSale, takeProductOffSale, updateCategory, updateProduct, uploadProductImage } from './api'
import { http } from './http'
import type { ApiResponse, Category, PageResponse, Product, ProductImage } from '../types/api'

const product: Product = {
  id: 12,
  categoryId: 3,
  pickupPointId: 1,
  name: '校园纪念册',
  saleType: 'NORMAL',
  status: 'DRAFT',
  price: 29.9,
  stock: 20,
  soldCount: 0,
  limitPerUser: 1,
  createdAt: '2026-08-24T10:00:00',
}

const image: ProductImage = {
  id: 91,
  productId: product.id,
  objectName: 'image.png',
  displayName: '校园纪念册封面',
  url: '/api/v1/files/images/image.png',
  contentType: 'image/png',
  sizeBytes: 8,
  sortOrder: 0,
  createdAt: '2026-08-24T10:00:00',
}

describe('product image management API', () => {
  afterEach(() => vi.restoreAllMocks())

  it('loads the protected management catalog', async () => {
    const page: PageResponse<Product> = { items: [product], total: 1, page: 1, size: 100, totalPages: 1 }
    const getSpy = vi.spyOn(http, 'get').mockResolvedValue({
      data: { success: true, code: 'OK', message: 'success', data: page } satisfies ApiResponse<PageResponse<Product>>,
    })

    await expect(getManagedProducts({ size: 100 })).resolves.toEqual(page)
    expect(getSpy).toHaveBeenCalledWith('/admin/products', { params: { size: 100 } })
  })

  it('uploads the selected file as multipart form data', async () => {
    const postSpy = vi.spyOn(http, 'post').mockResolvedValue({
      data: { success: true, code: 'OK', message: 'success', data: image } satisfies ApiResponse<ProductImage>,
    })
    const file = new File([new Uint8Array([1, 2, 3])], 'image.png', { type: 'image/png' })

    await expect(uploadProductImage(product.id, file, '校园纪念册封面', 2)).resolves.toEqual(image)
    const form = postSpy.mock.calls[0][1] as FormData
    expect(form.get('file')).toBe(file)
    expect(form.get('displayName')).toBe('校园纪念册封面')
    expect(form.get('sortOrder')).toBe('2')
  })

  it('deletes the owned image through the protected endpoint', async () => {
    const deleteSpy = vi.spyOn(http, 'delete').mockResolvedValue({
      data: { success: true, code: 'OK', message: 'success', data: null } satisfies ApiResponse<null>,
    })

    await expect(deleteProductImage(product.id, image.id)).resolves.toBeUndefined()
    expect(deleteSpy).toHaveBeenCalledWith(`/admin/products/${product.id}/images/${image.id}`)
  })

  it('creates and updates products through operator endpoints', async () => {
    const payload = {
      categoryId: product.categoryId,
      pickupPointId: product.pickupPointId,
      name: product.name,
      saleType: product.saleType,
      price: product.price,
      stock: product.stock,
      limitPerUser: product.limitPerUser,
    }
    const postSpy = vi.spyOn(http, 'post').mockResolvedValue({
      data: { success: true, code: 'OK', message: 'success', data: product } satisfies ApiResponse<Product>,
    })
    const putSpy = vi.spyOn(http, 'put').mockResolvedValue({
      data: { success: true, code: 'OK', message: 'success', data: product } satisfies ApiResponse<Product>,
    })

    await expect(createProduct(payload)).resolves.toEqual(product)
    await expect(updateProduct(product.id, payload)).resolves.toEqual(product)
    expect(postSpy).toHaveBeenCalledWith('/products', payload)
    expect(putSpy).toHaveBeenCalledWith(`/admin/products/${product.id}`, payload)
  })

  it('updates product lifecycle through protected endpoints', async () => {
    const postSpy = vi.spyOn(http, 'post').mockResolvedValue({
      data: { success: true, code: 'OK', message: 'success', data: product } satisfies ApiResponse<Product>,
    })
    const deleteSpy = vi.spyOn(http, 'delete').mockResolvedValue({
      data: { success: true, code: 'OK', message: 'success', data: null } satisfies ApiResponse<null>,
    })

    await takeProductOffSale(product.id)
    await putProductOnSale(product.id)
    await deleteProduct(product.id)

    expect(postSpy).toHaveBeenNthCalledWith(1, `/admin/products/${product.id}/off-sale`)
    expect(postSpy).toHaveBeenNthCalledWith(2, `/admin/products/${product.id}/on-sale`)
    expect(deleteSpy).toHaveBeenCalledWith(`/admin/products/${product.id}`)
  })

  it('manages categories through protected endpoints', async () => {
    const category: Category = { id: 3, name: '纪念品', sortOrder: 2, status: 'ACTIVE', createdAt: product.createdAt }
    const getSpy = vi.spyOn(http, 'get').mockResolvedValue({
      data: { success: true, code: 'OK', message: 'success', data: [category] } satisfies ApiResponse<Category[]>,
    })
    const postSpy = vi.spyOn(http, 'post').mockResolvedValue({
      data: { success: true, code: 'OK', message: 'success', data: category } satisfies ApiResponse<Category>,
    })
    const putSpy = vi.spyOn(http, 'put').mockResolvedValue({
      data: { success: true, code: 'OK', message: 'success', data: category } satisfies ApiResponse<Category>,
    })
    const deleteSpy = vi.spyOn(http, 'delete').mockResolvedValue({
      data: { success: true, code: 'OK', message: 'success', data: null } satisfies ApiResponse<null>,
    })

    await expect(getManagedCategories()).resolves.toEqual([category])
    await createCategory({ name: category.name, sortOrder: category.sortOrder })
    await updateCategory(category.id, { name: category.name, sortOrder: category.sortOrder, status: 'INACTIVE' })
    await deleteCategory(category.id)

    expect(getSpy).toHaveBeenCalledWith('/admin/categories')
    expect(postSpy).toHaveBeenCalledWith('/admin/categories', { name: category.name, sortOrder: category.sortOrder })
    expect(putSpy).toHaveBeenCalledWith(`/admin/categories/${category.id}`, { name: category.name, sortOrder: category.sortOrder, status: 'INACTIVE' })
    expect(deleteSpy).toHaveBeenCalledWith(`/admin/categories/${category.id}`)
  })
})
