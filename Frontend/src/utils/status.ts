const labels: Record<string, string> = {
  UNPUBLISHED: '未发布', PENDING_REVIEW: '待审核', REJECTED: '已驳回', RESERVING: '预约中', PENDING: '待开始', RUNNING: '进行中', ENDED: '已结束', TERMINATED: '已终止',
  WAIT_PAYMENT: '待支付', PAID: '已支付', WAIT_VERIFICATION: '待核销', COMPLETED: '已完成', CANCELLED: '已取消', REFUNDING: '退款中', REFUNDED: '已退款',
  NORMAL: '常规发售', FLASH_SALE: '限时抢购', PRE_SALE: '预售', LOTTERY: '预约抽签',
  QUALIFIED: '已获资格', NOT_QUALIFIED: '未中签', ON_SALE: '在售', OFF_SALE: '已下架',
  APPROVED: '已通过', RESOLVED: '已成立',
}

const tones: Record<string, string> = {
  RESERVING: 'coral', RUNNING: 'blue', QUALIFIED: 'blue', COMPLETED: 'blue', WAIT_VERIFICATION: 'blue', ON_SALE: 'blue',
  PENDING: 'yellow', PENDING_REVIEW: 'yellow', WAIT_PAYMENT: 'yellow', PRE_SALE: 'yellow', LOTTERY: 'coral', FLASH_SALE: 'coral', REFUNDING: 'yellow', REJECTED: 'coral',
  APPROVED: 'blue', RESOLVED: 'blue', ENDED: 'muted', TERMINATED: 'muted', CANCELLED: 'muted', REFUNDED: 'muted', OFF_SALE: 'muted', NOT_QUALIFIED: 'muted',
}

export const getStatusLabel = (status: string) => labels[status] || status
export const getStatusTone = (status: string) => tones[status] || 'ink'
