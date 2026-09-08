const assert = require('assert')

const {
  createAvailabilityQuery,
  refreshMobileTransferAvailability
} = require('../src/views/mobile/feature/mobileTransferAvailability')

async function run() {
  const original = {
    transferId: 144,
    fromDeptId: 100,
    fromWarehouseId: 100,
    details: [
      { itemType: 'product', itemId: 182, itemName: '白茶富阳专用', quantity: 2, availableQuantity: 7 },
      { itemType: 'gift', itemId: 30, itemName: '测试礼盒', quantity: 1, costPrice: 8 }
    ]
  }
  const queries = []
  const refreshed = await refreshMobileTransferAvailability(original, query => {
    queries.push(query)
    if (query.itemType === 'product') {
      return Promise.resolve({
        rows: [{ itemType: 'product', itemId: 182, currentQuantity: 1000, lockedQuantity: 0, costPrice: 12.5 }]
      })
    }
    return Promise.resolve({ rows: [] })
  })

  assert.deepStrictEqual(
    queries,
    [
      {
        pageNum: 1,
        pageSize: 1,
        itemType: 'product',
        itemId: 182,
        shopDeptId: 100,
        warehouseId: 100,
        transferSource: true
      },
      {
        pageNum: 1,
        pageSize: 1,
        itemType: 'gift',
        itemId: 30,
        shopDeptId: 100,
        warehouseId: 100,
        transferSource: true
      }
    ],
    'restored mobile drafts should recheck every material against the actual source organization'
  )
  assert.strictEqual(refreshed.details[0].availableQuantity, 1000)
  assert.strictEqual(refreshed.details[0].availabilityStatus, 'loaded')
  assert.strictEqual(refreshed.details[0].costPrice, 12.5)
  assert.strictEqual(refreshed.details[1].availableQuantity, 0)
  assert.strictEqual(refreshed.details[1].availabilityStatus, 'loaded')
  assert.strictEqual(refreshed.details[1].costPrice, 8)
  assert.strictEqual(original.details[0].availableQuantity, 7, 'availability refresh must not mutate the retained detail source')

  assert.deepStrictEqual(
    createAvailabilityQuery({ fromDeptId: 9, fromWarehouseId: 10 }, { itemType: 'oe', itemId: 88 }),
    {
      pageNum: 1,
      pageSize: 1,
      itemType: 'oe',
      itemId: 88,
      shopDeptId: 9,
      warehouseId: 10,
      transferSource: true
    }
  )

  await assert.rejects(
    refreshMobileTransferAvailability({ details: [{ itemType: 'product', itemId: 1 }] }, () => Promise.resolve({ rows: [] })),
    /草稿缺少来源组织/,
    'draft editing must fail closed when its source organization is missing'
  )
  await assert.rejects(
    refreshMobileTransferAvailability({ fromDeptId: 100, details: [{ itemType: 'product', itemId: 1 }] }, () => {
      return Promise.reject(new Error('当前门店不允许向该仓库要货'))
    }),
    /当前门店不允许向该仓库要货/,
    'source inventory failures must reject instead of being converted to zero availability'
  )

  console.log('mobile transfer availability tests passed')
}

run().catch(error => {
  console.error(error)
  process.exitCode = 1
})
