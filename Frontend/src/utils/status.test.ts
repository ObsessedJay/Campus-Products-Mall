import { describe, expect, it } from 'vitest'
import { getStatusLabel, getStatusTone } from './status'

describe('status presentation', () => {
  it('maps backend activity and order states to clear Chinese labels', () => {
    expect(getStatusLabel('RESERVING')).toBe('预约中')
    expect(getStatusLabel('WAIT_VERIFICATION')).toBe('待核销')
    expect(getStatusLabel('APPROVED')).toBe('已通过')
    expect(getStatusTone('APPROVED')).toBe('blue')
  })

  it('preserves unknown states for forward compatibility', () => {
    expect(getStatusLabel('NEW_BACKEND_STATE')).toBe('NEW_BACKEND_STATE')
    expect(getStatusTone('NEW_BACKEND_STATE')).toBe('ink')
  })
})
