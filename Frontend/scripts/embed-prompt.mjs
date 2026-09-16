import fs from 'node:fs'
import path from 'node:path'

const PNG_SIGNATURE = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])

function crc32(buffer) {
  let crc = 0xffffffff
  for (const byte of buffer) {
    crc ^= byte
    for (let bit = 0; bit < 8; bit += 1) {
      crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1))
    }
  }
  return (crc ^ 0xffffffff) >>> 0
}

function chunk(type, data) {
  const typeBuffer = Buffer.from(type, 'ascii')
  const output = Buffer.alloc(12 + data.length)
  output.writeUInt32BE(data.length, 0)
  typeBuffer.copy(output, 4)
  data.copy(output, 8)
  output.writeUInt32BE(crc32(Buffer.concat([typeBuffer, data])), 8 + data.length)
  return output
}

function textChunk(keyword, value) {
  return chunk('tEXt', Buffer.from(`${keyword}\0${value}`, 'latin1'))
}

function stripManagedTextChunks(png) {
  const kept = [PNG_SIGNATURE]
  let offset = PNG_SIGNATURE.length

  while (offset < png.length) {
    const length = png.readUInt32BE(offset)
    const type = png.toString('ascii', offset + 4, offset + 8)
    const dataStart = offset + 8
    const chunkEnd = dataStart + length + 4
    const keyword = type === 'tEXt'
      ? png.toString('latin1', dataStart, dataStart + length).split('\0', 1)[0]
      : ''

    if (!['Prompt', 'Origin', 'Asset-Type', 'Production'].includes(keyword) && type !== 'IEND') {
      kept.push(png.subarray(offset, chunkEnd))
    }
    if (type === 'IEND') break
    offset = chunkEnd
  }
  return kept
}

function embed(file, row) {
  const png = fs.readFileSync(file)
  if (!png.subarray(0, 8).equals(PNG_SIGNATURE)) throw new Error(`${file} is not a PNG`)
  const chunks = stripManagedTextChunks(png)
  chunks.push(textChunk('Prompt', row.prompt))
  chunks.push(textChunk('Origin', row.origin))
  chunks.push(textChunk('Asset-Type', row.assetType))
  chunks.push(textChunk('Production', 'Deterministic local source composite; no generative API used.'))
  chunks.push(chunk('IEND', Buffer.alloc(0)))
  fs.writeFileSync(file, Buffer.concat(chunks))
}

const args = process.argv.slice(2)
const inventoryFlag = args.indexOf('--inventory')
if (inventoryFlag === -1 || !args[inventoryFlag + 1]) {
  console.error('Usage: node scripts/embed-prompt.mjs --inventory src/assets/media-inventory.json')
  process.exit(1)
}

const inventoryPath = path.resolve(args[inventoryFlag + 1])
const inventory = JSON.parse(fs.readFileSync(inventoryPath, 'utf8'))
for (const row of inventory) {
  const file = path.resolve(path.dirname(inventoryPath), row.file)
  embed(file, row)
  console.log(`embedded ${path.relative(process.cwd(), file)}`)
}
