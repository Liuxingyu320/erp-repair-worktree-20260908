const ROUTE_KEYS_BY_PATH = {
  "/mobile/store": "storeWorkbench",
  "/mobile/warehouse": "warehouseWorkbench",
  "/mobile/inventory": "workbench",
  "/mobile/todo": "todo",
  "/mobile/contract": "contract",
  "/mobile/sign-package": "signPackage",
  "/mobile/onboard-data": "onboardData"
}

const SIGN_EXCEL_IMPORT_ENABLED =
  String(process.env.VUE_APP_SIGN_EXCEL_IMPORT_ENABLED || "true").trim().toLowerCase() === "true"

const mobileRouteDefinitions = [
  {
    path: '/mobile/store',
    component: () => import('@/views/mobile/store/index'),
    hidden: true,
    meta: { title: '店铺工作台' }
  },
  {
    path: '/mobile/warehouse',
    component: () => import('@/views/mobile/warehouse/index'),
    hidden: true,
    meta: { title: '仓库工作台' }
  },
  {
    path: '/mobile/inventory',
    component: () => import('@/views/mobile/inventory/index'),
    hidden: true,
    meta: { title: '进销存工作台' }
  },
  {
    path: '/mobile/hr',
    component: () => import('@/views/mobile/hr/index'),
    hidden: true,
    meta: {
      title: '人事工作台',
      mobileFeature: {
        featureKey: 'hrWorkbench',
        permissions: ['hr:onboarding:workbench'],
        requiresBusinessContext: false,
        title: '人事工作台',
        heading: '人事工作台',
        subtitle: '移动端入职办理和人事待办入口',
        icon: 'user',
        tone: 'teal',
        listTitle: '入职待办'
      }
    }
  },
  {
    path: '/mobile/hr/onboarding',
    component: () => import('@/views/mobile/hr/onboarding/index'),
    hidden: true,
    meta: {
      title: '入职任务',
      permissions: 'hr:onboarding:list',
      mobileFeature: {
        featureKey: 'hrOnboardingList',
        permissions: ['hr:onboarding:list'],
        requiresBusinessContext: false,
        title: '入职任务',
        heading: '入职任务',
        subtitle: '查看和处理员工入职任务',
        icon: 'document',
        tone: 'teal',
        listTitle: '入职任务'
      }
    }
  },
  {
    path: '/mobile/hr/onboarding/create',
    component: () => import('@/views/mobile/hr/onboarding/form'),
    hidden: true,
    meta: {
      title: '新建入职',
      mobileFeature: {
        featureKey: 'hrOnboardingAdd',
        permissions: ['hr:onboarding:add'],
        requiresBusinessContext: false,
        title: '新建入职'
      }
    }
  },
  {
    path: '/mobile/hr/onboarding/:id(\\d+)/edit',
    routeMatcher: /^\/mobile\/hr\/onboarding\/\d+\/edit$/,
    component: () => import('@/views/mobile/hr/onboarding/form'),
    hidden: true,
    meta: {
      title: '编辑入职',
      mobileFeature: {
        featureKey: 'hrOnboardingEdit',
        permissions: ['hr:onboarding:edit'],
        requiresBusinessContext: false,
        title: '编辑入职'
      }
    }
  },
  {
    path: '/mobile/hr/onboarding/:id(\\d+)',
    routeMatcher: /^\/mobile\/hr\/onboarding\/\d+$/,
    component: () => import('@/views/mobile/hr/onboarding/detail'),
    hidden: true,
    meta: {
      title: '入职详情',
      mobileFeature: {
        featureKey: 'hrOnboardingDetail',
        permissions: ['hr:onboarding:query'],
        requiresBusinessContext: false,
        title: '入职详情'
      }
    }
  },
  {
    path: '/mobile/drive',
    component: () => import('@/views/mobile/drive/index'),
    hidden: true,
    meta: {
      title: '手机云盘',
      mobileFeature: {
        featureKey: 'drive',
        permissions: ['drive:access'],
        title: '云盘',
        heading: '企业云盘',
        subtitle: '个人文件、公司公共盘和部门资料',
        icon: 'cloud',
        tone: 'blue'
      }
    }
  },
  {
    path: '/mobile/todo',
    component: () => import('@/views/mobile/todo/index'),
    hidden: true,
    meta: { title: '我的待办' }
  },
  {
    path: '/mobile/oa-purchase-approval',
    component: () => import('@/views/mobile/oa/purchaseApproval/index'),
    hidden: true,
    meta: {
      title: '采购审批',
      permissions: 'oa:todo:approve',
      mobileFeature: {
        featureKey: 'oaPurchaseApproval',
        permissions: ['oa:todo:approve'],
        requiresBusinessContext: false,
        title: '采购审批'
      }
    }
  },
  {
    path: '/mobile/reimbursement',
    component: () => import('@/views/mobile/oa/reimbursement/index'),
    hidden: true,
    meta: {
      title: '费用报销',
      permissions: [
        'oa:reimbursement:self',
        'oa:reimbursement:approve',
        'oa:reimbursement:finance:approve',
        'oa:reimbursement:finance:list'
      ],
      mobileFeature: {
        featureKey: 'reimbursement',
        permissions: [
          'oa:reimbursement:self',
          'oa:reimbursement:approve',
          'oa:reimbursement:finance:approve',
          'oa:reimbursement:finance:list'
        ],
        requiresBusinessContext: true,
        title: '费用报销'
      }
    }
  },
  {
    path: '/mobile/hr/employee',
    component: () => import('@/views/mobile/hr/employee/index'),
    hidden: true,
    meta: { title: '员工档案', permissions: 'hr:employee:list' }
  },
  {
    path: '/mobile/hr/completeness',
    component: () => import('@/views/mobile/hr/completeness/index'),
    hidden: true,
    meta: { title: '资料完整度', permissions: 'hr:completeness:list' }
  },
  {
    path: '/mobile/hr/health-certificate',
    component: () => import('@/views/mobile/hr/healthCertificate/index'),
    hidden: true,
    meta: {
      title: '健康证',
      permissions: [
        'hr:healthCertificate:self:edit',
        'hr:healthCertificate:list',
        'hr:healthCertificate:review'
      ]
    }
  },
  {
    path: '/mobile/sales',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机销售',
      mobileFeature: {
        featureKey: 'sales',
        allowedDeptTypes: ['STORE'],
        title: '销售',
        heading: '销售开单',
        subtitle: '手机网页端销售单据、待发货和客户订单入口',
        icon: 'sales',
        tone: 'blue',
        listTitle: '销售待处理',
        defaultQuery: { status: 'submitted' },
        actions: [
          { label: '待发货', icon: 'sales', query: { status: 'submitted' } },
          { label: '全部销售', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/purchase',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机采购',
      mobileFeature: {
        featureKey: 'purchase',
        allowedDeptTypes: ['WAREHOUSE'],
        title: '采购',
        heading: '采购入库',
        subtitle: '手机网页端采购到货、入库确认和供应商单据入口',
        icon: 'purchase',
        tone: 'amber',
        listTitle: '采购待处理',
        defaultQuery: { status: 'submitted' },
        actions: [
          { label: '待入库', icon: 'inbound', query: { status: 'submitted' } },
          { label: '采购退货', icon: 'return', path: '/mobile/purchase-return', permissions: ['inv:purchaseReturn:list'] },
          { label: '全部采购', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/purchase-return',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机采购退货',
      mobileFeature: {
        featureKey: 'purchaseReturn',
        allowedDeptTypes: ['WAREHOUSE'],
        title: '采购退货',
        heading: '采购退货',
        subtitle: '手机网页端供应商退货、退货确认和仓库出库入口',
        icon: 'return',
        tone: 'amber',
        listTitle: '采购退货待处理',
        defaultQuery: { status: 'submitted' },
        actions: [
          { label: '待确认', icon: 'return', query: { status: 'submitted' } },
          { label: '全部退货', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/stock',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机库存',
      mobileFeature: {
        featureKey: 'stock',
        permissions: ['inv:stock:list'],
        title: '库存',
        heading: '库存管理',
        subtitle: '手机网页端库存余额、低库存预警和商品库存入口',
        icon: 'search',
        tone: 'blue',
        listTitle: '库存提醒',
        defaultQuery: { stockScope: 'warning' },
        actions: [
          { label: '预警库存', icon: 'trend', query: { stockScope: 'warning' } },
          { label: '全部库存', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/product',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机商品',
      mobileFeature: {
        featureKey: 'product',
        title: '商品',
        heading: '商品资料',
        subtitle: '手机网页端商品编码、分类、供应商和价格资料查询入口',
        icon: 'cube',
        tone: 'blue',
        listTitle: '商品资料',
        defaultQuery: { status: '0' },
        actions: [
          { label: '启用商品', icon: 'cube', query: { status: '0' } },
          { label: '全部商品', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/oe',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机OE资料',
      mobileFeature: {
        featureKey: 'oe',
        title: 'OE资料',
        heading: 'OE资料',
        subtitle: '手机网页端OE器皿编码、分类、成本价和供应商资料查询入口',
        icon: 'cube',
        tone: 'teal',
        listTitle: 'OE资料',
        defaultQuery: { status: '0' },
        actions: [
          { label: '启用OE', icon: 'cube', query: { status: '0' } },
          { label: '停用OE', icon: 'warning', query: { status: '1' } },
          { label: '全部OE', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/gift',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机礼盒资料',
      mobileFeature: {
        featureKey: 'gift',
        title: '礼盒资料',
        heading: '礼盒资料',
        subtitle: '手机网页端礼盒编码、等级规格和指导售价查询入口',
        icon: 'document',
        tone: 'amber',
        listTitle: '礼盒资料',
        defaultQuery: { status: '0' },
        actions: [
          { label: '启用礼盒', icon: 'document', query: { status: '0' } },
          { label: '停用礼盒', icon: 'warning', query: { status: '1' } },
          { label: '全部礼盒', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/category',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机分类',
      mobileFeature: {
        featureKey: 'category',
        title: '分类',
        heading: '商品分类',
        subtitle: '手机网页端商品分类树、分类编码和启停状态查询入口',
        icon: 'document',
        tone: 'teal',
        listTitle: '分类资料',
        actions: [
          { label: '正常分类', icon: 'check', query: { status: '0' } },
          { label: '全部分类', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/customer',
    component: () => import('@/views/mobile/customer/index'),
    hidden: true,
    meta: {
      title: '手机客户',
      mobileFeature: {
        featureKey: 'customer',
        allowedDeptTypes: ['STORE'],
        permissions: ['inv:customerCard:list'],
        title: '客户服务卡',
        heading: '客户服务卡',
        subtitle: '查看本门店客户偏好、注意事项、人均预算和服务记录',
        icon: 'user',
        tone: 'teal',
        listTitle: '本店客户服务卡',
        defaultQuery: { status: '0' },
        actions: [
          { label: '正常客户', icon: 'user', query: { status: '0' } },
          { label: '全部客户', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/supplier',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机供应商',
      mobileFeature: {
        featureKey: 'supplier',
        allowedDeptTypes: ['WAREHOUSE'],
        title: '供应商',
        heading: '供应商资料',
        subtitle: '手机网页端供应商编码、联系人、合作状态和供货商品查询入口',
        icon: 'warehouse',
        tone: 'amber',
        listTitle: '供应商资料',
        defaultQuery: { status: '0' },
        actions: [
          { label: '合作中', icon: 'warehouse', query: { cooperationStatus: '0', status: '0' } },
          { label: '全部供应商', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/stock-log',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机库存流水',
      mobileFeature: {
        featureKey: 'stockLog',
        title: '流水',
        heading: '库存流水',
        subtitle: '手机网页端采购、销售、退货、盘点和调拨库存变动查询入口',
        icon: 'document',
        tone: 'blue',
        listTitle: '库存流水',
        actions: [
          { label: '销售出库', icon: 'truck', query: { movementType: 'sales_out' } },
          { label: '采购入库', icon: 'inbound', query: { movementType: 'purchase_in' } },
          { label: '全部流水', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/stock-check',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机盘点',
      mobileFeature: {
        featureKey: 'stockCheck',
        allowedDeptTypes: ['STORE', 'WAREHOUSE'],
        title: '盘点',
        heading: '库存盘点',
        subtitle: '手机网页端盘点任务、差异复核和库存校准入口',
        icon: 'check',
        tone: 'teal',
        listTitle: '盘点待处理',
        defaultQuery: { status: 'draft' },
        actions: [
          { label: '待录入', icon: 'edit', query: { status: 'draft' } },
          { label: '已退回', icon: 'return', query: { status: 'returned' } },
          { label: '已驳回', icon: 'return', query: { status: 'rejected' } },
          { label: '待我审批', icon: 'check', query: { approvalTodo: true }, permissions: ['inv:stockCheck:approve'] },
          { label: '全部盘点', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/transfer-records',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机调拨记录',
      mobileFeature: {
        featureKey: 'transferRecords',
        allowedDeptTypes: ['STORE', 'WAREHOUSE'],
        title: '调拨记录',
        heading: '调拨记录',
        subtitle: '手机网页端已完成、取消、驳回调拨记录和批次明细查询入口',
        icon: 'warehouse',
        tone: 'teal',
        listTitle: '调拨记录',
        actions: [
          { label: '已完成', icon: 'check', query: { status: 'received' } },
          { label: '全部记录', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/sales-return',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机退货',
      mobileFeature: {
        featureKey: 'salesReturn',
        allowedDeptTypes: ['STORE'],
        title: '退货',
        heading: '销售退货',
        subtitle: '手机网页端客户退货、单据确认和门店售后入口',
        icon: 'document',
        tone: 'amber',
        listTitle: '退货待处理',
        defaultQuery: { businessType: 'return' },
        actions: [
          { label: '待确认', icon: 'document', query: { businessType: 'return', status: 'submitted' } },
          { label: '全部退货', icon: 'search', query: { businessType: 'return' } }
        ]
      }
    }
  },
  {
    path: '/mobile/replenishment',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机补货',
      mobileFeature: {
        featureKey: 'replenishment',
        allowedDeptTypes: ['STORE'],
        title: '补货',
        heading: '补货申请',
        subtitle: '手机网页端门店要货、补货提交和库存组织协同入口',
        icon: 'inbound',
        tone: 'teal',
        listTitle: '补货申请',
        defaultQuery: { transferType: 'warehouse' },
        actions: [
          { label: '全部申请', icon: 'search', query: { transferType: 'warehouse' } },
          { label: '草稿', icon: 'document', query: { transferType: 'warehouse', status: 'draft' } },
          { label: '待审批', icon: 'check', query: { transferType: 'warehouse', status: 'submitted' } },
          { label: '待发货', icon: 'inbound', query: { transferType: 'warehouse', status: 'approved' } },
          { label: '待收货', icon: 'warehouse', query: { transferType: 'warehouse', statusGroup: 'receivable' } }
        ]
      }
    }
  },
  {
    path: '/mobile/outbound',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机出库',
      mobileFeature: {
        featureKey: 'outbound',
        allowedDeptTypes: ['WAREHOUSE'],
        title: '出库',
        heading: '发货处理',
        subtitle: '手机网页端仓库拣货、出库确认和销售发货入口',
        icon: 'truck',
        tone: 'teal',
        listTitle: '出库待处理',
        defaultQuery: { statusGroup: 'deliverable' },
        actions: [
          { label: '待出库', icon: 'truck', query: { statusGroup: 'deliverable' } },
          { label: '全部单据', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/transfer',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机调拨',
      mobileFeature: {
        featureKey: 'transfer',
        allowedDeptTypes: ['STORE', 'WAREHOUSE'],
        title: '调拨',
        heading: '调拨管理',
        subtitle: '门店发起要货、返仓和异店调货；仓库处理发货、收货与差异',
        icon: 'warehouse',
        tone: 'teal',
        listTitle: '调拨待处理',
        defaultQuery: {},
        actions: [
          {
            label: '发起要货',
            icon: 'inbound',
            behavior: 'create-form',
            transferType: 'warehouse',
            allowedDeptTypes: ['STORE'],
            permissions: ['inv:transfer:add', 'inv:transfer:edit']
          },
          {
            label: '异店调货',
            icon: 'warehouse',
            behavior: 'create-form',
            transferType: 'cross_store',
            allowedDeptTypes: ['STORE'],
            permissions: ['inv:transfer:add', 'inv:transfer:edit']
          },
          {
            label: '门店返仓',
            icon: 'return',
            behavior: 'create-form',
            transferType: 'store_return',
            allowedDeptTypes: ['STORE'],
            featureFlag: 'storeReturn',
            permissions: ['inv:transfer:add', 'inv:transfer:edit']
          },
          { label: '全部流转', icon: 'search', query: {} },
          { label: '门店要货', icon: 'inbound', query: { transferType: 'warehouse' } },
          { label: '异店调货记录', icon: 'warehouse', query: { transferType: 'cross_store' } },
          { label: '门店返仓记录', icon: 'return', query: { transferType: 'store_return' }, featureFlag: 'storeReturn' },
          { label: '待审批', icon: 'warehouse', query: { status: 'submitted' } },
          { label: '待收货', icon: 'inbound', query: { statusGroup: 'receivable', direction: 'receive' } },
          { label: '待发货', icon: 'truck', query: { statusGroup: 'deliverable', direction: 'deliver' } }
        ]
      }
    }
  },
  {
    path: '/mobile/transfer-approval',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '调拨审批',
      mobileFeature: {
        featureKey: 'transferApproval',
        allowedDeptTypes: ['STORE', 'WAREHOUSE'],
        title: '审批管理',
        heading: '调拨审批',
        subtitle: '手机网页端查看并处理当前账号需要审批的调拨单',
        icon: 'check',
        tone: 'teal',
        listTitle: '我的待审批',
        defaultQuery: { status: 'submitted' },
        actions: [
          { label: '待审批', icon: 'check', query: { status: 'submitted' } },
          { label: '刷新待办', icon: 'refresh', query: { status: 'submitted' } },
          { label: '调拨处理', icon: 'warehouse', path: '/mobile/transfer', permissions: ['inv:transfer:list'] }
        ]
      }
    }
  },
  {
    path: '/mobile/fixed-asset-repair',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '资产报修',
      mobileFeature: {
        featureKey: 'fixedAssetRepair',
        allowedDeptTypes: ['STORE'],
        title: '资产报修',
        heading: '固定资产维修上报',
        subtitle: '门店固定资产报修与可用额度；超额时按同款参考自行购买',
        icon: 'document',
        tone: 'amber',
        listTitle: '维修上报',
        defaultQuery: {},
        actions: [
          { label: '历史待确认', icon: 'search', query: { status: 'pending_confirm' } },
          { label: '全部报修', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/attendance',
    component: () => import('@/views/mobile/attendance/index'),
    hidden: true,
    meta: {
      title: '手机考勤',
      permissions: [
        'oa:attendance:punch:self',
        'oa:attendance:record:self',
        'oa:attendance:leave:self',
        'oa:attendance:leave:list',
        'oa:attendance:leave:approve',
        'oa:attendance:correction:self',
        'oa:attendance:correction:list',
        'oa:attendance:correction:approve'
      ],
      mobileFeature: {
        featureKey: 'attendance',
        featureFlag: 'attendanceV2',
        title: '考勤',
        heading: '现场考勤',
        subtitle: '按已发布排班进行定位、拍照和服务端水印打卡',
        icon: 'check',
        tone: 'teal',
        permissions: [
          'oa:attendance:punch:self',
          'oa:attendance:record:self',
          'oa:attendance:leave:self',
          'oa:attendance:leave:list',
          'oa:attendance:leave:approve',
          'oa:attendance:correction:self',
          'oa:attendance:correction:list',
          'oa:attendance:correction:approve'
        ]
      }
    }
  },
  {
    path: '/mobile/salary',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机工资',
      mobileFeature: {
        featureKey: 'salary',
        title: '工资',
        heading: '我的工资',
        subtitle: '手机网页端工资月份、出勤统计和实发工资查询入口',
        icon: 'trend',
        tone: 'blue',
        listTitle: '工资记录',
        actions: [
          { label: '计算本月', icon: 'trend', actionId: 'calculateSalary' },
          { label: '全部工资', icon: 'search', query: { salaryMonthScope: 'all' } }
        ]
      }
    }
  },
  {
    path: '/mobile/notice',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机通知',
      mobileFeature: {
        featureKey: 'notice',
        title: '通知',
        heading: '通知公告',
        subtitle: '手机网页端通知公告、未读提醒和公告详情入口',
        icon: 'bell',
        tone: 'blue',
        listTitle: '通知公告',
        actions: [
          { label: '未读', icon: 'bell', query: { isRead: false } },
          { label: '全部公告', icon: 'search', query: {} },
          { label: '全部已读', icon: 'check', actionId: 'markAllNoticeRead' }
        ]
      }
    }
  },
  {
    path: '/mobile/profile',
    component: () => import('@/views/mobile/profile/index'),
    hidden: true,
    meta: {
      title: '手机个人资料',
      mobileFeature: {
        featureKey: 'profile',
        title: '个人',
        heading: '个人资料',
        subtitle: '手机网页端账号、手机号、邮箱和所属组织资料入口',
        icon: 'user',
        tone: 'teal',
        listTitle: '账号资料',
        actions: [
          { label: '刷新资料', icon: 'refresh', behavior: 'refresh' },
          { label: '通知公告', icon: 'bell', path: '/mobile/notice' },
          { label: '切换店铺', icon: 'warehouse', behavior: 'select-shop', contextualLabel: 'switchBusinessContext' },
          { label: '退出登录', icon: 'logout', behavior: 'logout' }
        ],
        contextActionLabels: {
          switchBusinessContext: {
            STORE: '切换店铺',
            WAREHOUSE: '切换仓库'
          }
        }
      }
    }
  },
  {
    path: '/mobile/system-user',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机用户管理',
      mobileFeature: {
        featureKey: 'systemUser',
        title: '用户',
        heading: '用户管理',
        subtitle: '手机网页端查看系统用户、所属组织、手机号和账号状态',
        icon: 'user',
        tone: 'blue',
        listTitle: '用户列表',
        actions: [
          { label: '正常用户', icon: 'user', query: { status: '0' } },
          { label: '停用用户', icon: 'document', query: { status: '1' } },
          { label: '全部用户', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/system-role',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机角色管理',
      mobileFeature: {
        featureKey: 'systemRole',
        title: '角色',
        heading: '角色管理',
        subtitle: '手机网页端查看系统角色、权限字符、显示顺序和启停状态',
        icon: 'user',
        tone: 'teal',
        listTitle: '角色列表',
        actions: [
          { label: '正常角色', icon: 'check', query: { status: '0' } },
          { label: '停用角色', icon: 'document', query: { status: '1' } },
          { label: '全部角色', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/system-post',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机岗位管理',
      mobileFeature: {
        featureKey: 'systemPost',
        title: '岗位',
        heading: '岗位管理',
        subtitle: '手机网页端查看岗位编码、岗位名称、排序和启停状态',
        icon: 'document',
        tone: 'teal',
        listTitle: '岗位列表',
        actions: [
          { label: '正常岗位', icon: 'check', query: { status: '0' } },
          { label: '停用岗位', icon: 'document', query: { status: '1' } },
          { label: '全部岗位', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/system-dept',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机部门管理',
      mobileFeature: {
        featureKey: 'systemDept',
        title: '组织',
        heading: '部门店仓',
        subtitle: '手机网页端查看部门、店铺、仓库、负责人和组织状态',
        icon: 'warehouse',
        tone: 'amber',
        listTitle: '组织列表',
        actions: [
          { label: '全部组织', icon: 'search', query: {} },
          { label: '门店', icon: 'warehouse', query: { deptType: 'STORE' } },
          { label: '仓库', icon: 'warehouse', query: { deptType: 'WAREHOUSE' } }
        ]
      }
    }
  },
  {
    path: '/mobile/system-menu',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机菜单管理',
      mobileFeature: {
        featureKey: 'systemMenu',
        title: '菜单',
        heading: '菜单管理',
        subtitle: '手机网页端查看菜单名称、类型、权限标识、路由和组件路径',
        icon: 'document',
        tone: 'blue',
        listTitle: '菜单列表',
        actions: [
          { label: '全部菜单', icon: 'search', query: {} },
          { label: '目录', icon: 'document', query: { menuType: 'M' } },
          { label: '菜单', icon: 'document', query: { menuType: 'C' } },
          { label: '按钮', icon: 'check', query: { menuType: 'F' } },
          { label: '正常菜单', icon: 'check', query: { status: '0' } }
        ]
      }
    }
  },
  {
    path: '/mobile/user-shop',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机店仓授权',
      mobileFeature: {
        featureKey: 'userShop',
        title: '店仓授权',
        heading: '用户店仓授权',
        subtitle: '手机网页端查看用户可管理的门店、仓库授权范围和账号状态',
        icon: 'warehouse',
        tone: 'teal',
        listTitle: '授权用户',
        actions: [
          { label: '全部授权', icon: 'search', query: {} },
          { label: '正常用户', icon: 'user', query: { status: '0' } },
          { label: '停用用户', icon: 'document', query: { status: '1' } }
        ]
      }
    }
  },
  {
    path: '/mobile/system-config',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机参数设置',
      mobileFeature: {
        featureKey: 'systemConfig',
        title: '参数',
        heading: '参数设置',
        subtitle: '手机网页端查看参数键名、键值、类型和启停状态',
        icon: 'document',
        tone: 'blue',
        listTitle: '参数列表',
        actions: [
          { label: '全部参数', icon: 'search', query: {} },
          { label: '系统内置', icon: 'check', query: { configType: 'Y' } },
          { label: '自定义', icon: 'document', query: { configType: 'N' } }
        ]
      }
    }
  },
  {
    path: '/mobile/system-dict-type',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机字典类型',
      mobileFeature: {
        featureKey: 'systemDictType',
        title: '字典',
        heading: '字典类型',
        subtitle: '手机网页端查看字典名称、类型、备注和启停状态',
        icon: 'document',
        tone: 'teal',
        listTitle: '字典类型',
        actions: [
          { label: '正常字典', icon: 'check', query: { status: '0' } },
          { label: '停用字典', icon: 'document', query: { status: '1' } },
          { label: '全部字典', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/system-dict-data',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机字典数据',
      mobileFeature: {
        featureKey: 'systemDictData',
        title: '字典数据',
        heading: '字典数据',
        subtitle: '手机网页端查看字典标签、键值、类型和排序',
        icon: 'document',
        tone: 'teal',
        listTitle: '字典数据',
        actions: [
          { label: '正常数据', icon: 'check', query: { status: '0' } },
          { label: '停用数据', icon: 'document', query: { status: '1' } },
          { label: '全部数据', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/system-logininfor',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机登录日志',
      mobileFeature: {
        featureKey: 'systemLogininfor',
        title: '登录日志',
        heading: '登录日志',
        subtitle: '手机网页端查看登录账号、地址、状态和访问时间',
        icon: 'document',
        tone: 'blue',
        listTitle: '登录日志',
        actions: [
          { label: '登录成功', icon: 'check', query: { status: '0' } },
          { label: '登录失败', icon: 'document', query: { status: '1' } },
          { label: '全部日志', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/system-operlog',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机操作日志',
      mobileFeature: {
        featureKey: 'systemOperlog',
        title: '操作日志',
        heading: '操作日志',
        subtitle: '手机网页端查看模块、操作类型、操作人和执行状态',
        icon: 'document',
        tone: 'amber',
        listTitle: '操作日志',
        actions: [
          { label: '执行成功', icon: 'check', query: { status: '0' } },
          { label: '执行异常', icon: 'document', query: { status: '1' } },
          { label: '全部日志', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/monitor-job',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机定时任务',
      mobileFeature: {
        featureKey: 'monitorJob',
        title: '定时任务',
        heading: '调度任务',
        subtitle: '手机网页端查看任务名称、任务组、Cron 表达式和运行状态',
        icon: 'trend',
        tone: 'blue',
        listTitle: '调度任务',
        actions: [
          { label: '正常任务', icon: 'check', query: { status: '0' } },
          { label: '暂停任务', icon: 'document', query: { status: '1' } },
          { label: '全部任务', icon: 'search', query: {} },
          { label: '调度日志', icon: 'document', path: '/mobile/monitor-job-log' }
        ]
      }
    }
  },
  {
    path: '/mobile/monitor-job-log',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机调度日志',
      mobileFeature: {
        featureKey: 'monitorJobLog',
        title: '调度日志',
        heading: '调度日志',
        subtitle: '手机网页端查看任务执行记录、耗时、异常和执行状态',
        icon: 'document',
        tone: 'blue',
        listTitle: '调度日志',
        actions: [
          { label: '执行成功', icon: 'check', query: { status: '0' } },
          { label: '执行失败', icon: 'document', query: { status: '1' } },
          { label: '全部日志', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/monitor-online',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机在线用户',
      mobileFeature: {
        featureKey: 'monitorOnline',
        title: '在线',
        heading: '在线用户',
        subtitle: '手机网页端查看在线账号、登录地址、浏览器和登录时间',
        icon: 'user',
        tone: 'teal',
        listTitle: '在线用户',
        actions: [
          { label: '全部在线', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/salary-scheme',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机薪资方案',
      mobileFeature: {
        featureKey: 'salaryScheme',
        title: '薪资方案',
        heading: '薪资方案',
        subtitle: '手机网页端查看薪资方案、基础工资、适用角色和启停状态',
        icon: 'trend',
        tone: 'amber',
        listTitle: '薪资方案',
        actions: [
          { label: '启用方案', icon: 'check', query: { status: '0' } },
          { label: '停用方案', icon: 'document', query: { status: '1' } },
          { label: '全部方案', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/transfer-rules',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '手机调拨规则',
      mobileFeature: {
        featureKey: 'transferRules',
        title: '调拨规则',
        heading: '调拨审批规则',
        subtitle: '手机网页端查看门店补货、仓间调拨审批规则和适用组织',
        icon: 'warehouse',
        tone: 'teal',
        listTitle: '调拨规则',
        actions: [
          { label: '正常规则', icon: 'check', query: { status: '0' } },
          { label: '停用规则', icon: 'document', query: { status: '1' } },
          { label: '全部规则', icon: 'search', query: {} }
        ]
      }
    }
  },
  {
    path: '/mobile/contract',
    component: () => import('@/views/mobile/contract/index'),
    hidden: true,
    meta: { title: '劳动合同' }
  },
  {
    path: '/mobile/sign-package',
    component: () => import('@/views/mobile/signPackage/index'),
    hidden: true,
    meta: { title: '我的签约' }
  },
  {
    path: '/mobile/onboard-data',
    redirect: route => ({ path: '/mobile/sign-package', query: route.query }),
    hidden: true,
    meta: { title: '我的签约' }
  },
  {
    path: '/mobile/mine',
    component: () => import('@/views/mobile/feature/index'),
    hidden: true,
    meta: {
      title: '我的',
      mobileFeature: {
        featureKey: 'mine',
        title: '我的',
        heading: '账号与店铺',
        subtitle: '手机网页端当前店铺、账号资料和切换入口',
        icon: 'user',
        tone: 'teal',
        listTitle: '账号操作',
        actions: [
          { label: '个人资料', icon: 'user', path: '/mobile/profile' },
          { label: '通知公告', icon: 'bell', path: '/mobile/notice' },
          { label: '云盘', icon: 'cloud', tone: 'blue', path: '/mobile/drive', permissions: ['drive:access'], featureFlag: 'drive', placement: 'more' },
          { label: '切换店铺', icon: 'warehouse', behavior: 'select-shop', contextualLabel: 'switchBusinessContext' },
          { label: '刷新状态', icon: 'refresh', behavior: 'refresh' },
          { label: '我的签约', icon: 'document', path: '/mobile/sign-package' },
          { label: '调拨审批', icon: 'check', path: '/mobile/transfer-approval', permissions: ['inv:transfer:approve'] },
          { label: '费用报销', icon: 'document', path: '/mobile/reimbursement', permissions: ['oa:reimbursement:self'] },
          {
            label: '考勤',
            icon: 'check',
            path: '/mobile/attendance',
            permissions: [
              'oa:attendance:punch:self',
              'oa:attendance:record:self',
              'oa:attendance:leave:self',
              'oa:attendance:correction:self'
            ],
            allowedDeptTypes: ['STORE'],
            featureFlag: 'attendanceV2'
          },
          { label: '工资', icon: 'trend', path: '/mobile/salary', allowedDeptTypes: ['STORE'] },
          { label: '资产报修', icon: 'document', path: '/mobile/fixed-asset-repair', permissions: ['oa:fixedAsset:repair:list'], allowedDeptTypes: ['STORE'] },
          { label: '退出登录', icon: 'logout', behavior: 'logout' }
        ],
        contextActionLabels: {
          switchBusinessContext: {
            STORE: '切换店铺',
            WAREHOUSE: '切换仓库'
          }
        },
        contextCopy: {
          STORE: {
            heading: '账号与店铺',
            subtitle: '手机网页端当前店铺、账号资料和切换入口'
          },
          WAREHOUSE: {
            heading: '账号与仓库',
            subtitle: '手机网页端当前仓库、账号资料和切换入口'
          }
        }
      }
    }
  },
]

const { applyMobileRoutePolicies } = require("./mobileRoutePolicy")
applyMobileRoutePolicies(mobileRouteDefinitions)

function getMobileRouteKey(definition) {
  return definition.key || ROUTE_KEYS_BY_PATH[definition.path] ||
    (definition.meta && definition.meta.mobileFeature && definition.meta.mobileFeature.featureKey) ||
    (definition.meta && definition.meta.featureKey)
}

const MOBILE_ROUTES = mobileRouteDefinitions.reduce((routes, definition) => {
  const key = getMobileRouteKey(definition)
  if (key) {
    routes[key] = definition.path
  }
  return routes
}, {})

module.exports = {
  mobileRouteDefinitions,
  MOBILE_ROUTES,
  SIGN_EXCEL_IMPORT_ENABLED
}
