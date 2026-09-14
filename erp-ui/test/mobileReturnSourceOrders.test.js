const assert = require("assert")

const {
  createSalesReturnDataFromOrder,
  createPurchaseReturnDataFromOrder
} = require("../src/views/mobile/feature/mobileReturnSourceOrders")

assert.deepStrictEqual(
  createSalesReturnDataFromOrder({
    orderId: "21",
    orderNo: "SO202607050001",
    customerId: "3",
    customerName: "北京柏悦客户",
    shopDeptId: "8",
    details: [
      {
        detailId: "51",
        productId: "5",
        productName: "龙井茶",
        sku: "SKU-5",
        spec: "500g",
        unit: "斤",
        deliveredQuantity: "2",
        unitPrice: "18"
      },
      {
        detailId: "52",
        productId: "6",
        productName: "不可退商品",
        deliveredQuantity: "0",
        unitPrice: "9"
      }
    ]
  }),
  {
    salesOrderId: 21,
    salesOrderNo: "SO202607050001",
    customerId: 3,
    customerName: "北京柏悦客户",
    shopDeptId: 8,
    returnTitle: "销售退货-SO202607050001",
    details: [{
      salesDetailId: 51,
      itemType: "product", itemId: 5, itemCode: "SKU-5", itemName: "龙井茶",
      productId: 5,
      productName: "龙井茶",
      sku: "SKU-5",
      spec: "500g",
      unit: "斤",
      quantity: 0,
      maxReturnQuantity: 2,
      unitPrice: 18,
      price: 18,
      amount: 0,
      returnedQuantity: 0,
      remark: ""
    }]
  },
  "sales return source order should hydrate header fields and returnable original details"
)

assert.deepStrictEqual(
  createSalesReturnDataFromOrder({
    orderId: "22",
    orderNo: "SO202607050002",
    customerName: "历史退货客户",
    shopDeptId: "8",
    details: [
      {
        detailId: "53",
        productId: "7",
        productName: "红茶",
        deliveredQuantity: "5",
        historicalReturnedQuantity: "2",
        unitPrice: "12"
      }
    ]
  }).details,
  [{
    salesDetailId: 53,
    itemType: "product", itemId: 7, itemName: "红茶",
    productId: 7,
    productName: "红茶",
    quantity: 0,
    maxReturnQuantity: 3,
    unitPrice: 12,
    price: 12,
    amount: 0,
    returnedQuantity: 2,
    remark: ""
  }],
  "sales return source details should start empty and subtract historical returns from the remaining quantity"
)

assert.deepStrictEqual(
  createPurchaseReturnDataFromOrder({
    orderId: "31",
    orderNo: "PO202607050001",
    supplierId: "7",
    supplierName: "主仓供应商",
    shopDeptId: "8",
    details: [
      {
        detailId: "61",
        itemType: "oe",
        itemId: "805",
        itemCode: "OE-805",
        itemName: "茶叶展架",
        productId: "5",
        productName: "龙井茶",
        sku: "SKU-5",
        spec: "500g",
        unit: "斤",
        receivedQuantity: "3",
        unitPrice: "9"
      }
    ]
  }),
  {
    purchaseOrderId: 31,
    purchaseOrderNo: "PO202607050001",
    supplierId: 7,
    supplierName: "主仓供应商",
    shopDeptId: 8,
    returnTitle: "采购退货-PO202607050001",
    details: [{
      purchaseDetailId: 61,
      itemType: "oe",
      itemId: 805,
      itemCode: "OE-805",
      itemName: "茶叶展架",
      productId: 5,
      productName: "茶叶展架",
      sku: "SKU-5",
      spec: "500g",
      unit: "斤",
      quantity: 0,
      maxReturnQuantity: 3,
      unitPrice: 9,
      price: 9,
      amount: 0,
      returnedQuantity: 0,
      remark: ""
    }]
  },
  "purchase return source order should hydrate header fields and returnable original details"
)
