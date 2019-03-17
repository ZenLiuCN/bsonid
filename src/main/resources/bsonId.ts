namespace Bson {
    const BsonIdRegx = /^[0-9a-fA-F]{24}$/
    const BsonShortIdRegx = /^[0-9a-zA-Z\-\_]{16}$/
    const code = '012345679abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_'
    const compress = function (hexBsonId: string) {
        if (hexBsonId.toLowerCase().match(BsonIdRegx) == null) {
            throw Error(`Invalid BsonId ${hexBsonId},should be 24 char for HEX`)
        }
        let res = ""
        const ar = hexBsonId.split("").map(c => char2int(c))
        for (let i = 0; i < ar.length; i += 3) {
            const pre = ar[i]
            const mid = ar[i + 1]
            const end = ar[i + 2]
            res += this.int2char(pre << 2 + mid >> 2)
            res += this.int2char(mid & 3 << 4 + end)
        }
        return res
    }
    const decompress = function (hexShortId: string) {
        if (hexShortId.match(BsonShortIdRegx) == null) {
            throw Error(`Invalid BsonShortId ${hexShortId},should be 16 char `)
        }
        let res = ""
        const ar = hexShortId.split("").map(c => char2int(c))
        for (let i = 0; i < ar.length; i += 2) {
            const pre = ar[i]
            const end = ar[i + 1]
            res += int2char(pre >> 2)
            res += int2char(pre & 3 << 2 + end >> 4)
            res += int2char(end & 15)
        }
        return res
    }
    const int2char = function (i: number) {
        if (i < 0 || i > 63) {
            throw Error(`invalid integer ${i} for char`)
        }
        return code[i]
    }
    const char2int = function (i: string) {
        if (i.length != 1) {
            throw Error(`invalid char ${i} for integer`)
        }
        const idx = code.indexOf(i)
        if (idx < 0) {
            throw Error(`invalid char ${i} for integer`)
        }
        return idx
    }
    class Buffer {
        private buffer = new ArrayBuffer(12)
        private _timestamp = new Uint8Array(this.buffer, 0, 4)
        private _machine = new Uint8Array(this.buffer, 4, 3)
        private _process = new Uint8Array(this.buffer, 7, 2)
        private _counter = new Uint8Array(this.buffer, 9, 3)

        //0123|456|78|9 10 11
        set timestamp(time: number) {
            this._timestamp[3] = time & 0xff
            this._timestamp[2] = (time >> 8) & 0xff
            this._timestamp[1] = (time >> 16) & 0xff
            this._timestamp[0] = (time >> 24) & 0xff
        }

        set processIdentifier(process: number) {
            this._process[1] = (process) & 0xff
            this._process[0] = (process >> 8) & 0xff
        }

        set machineIdentifier(machine: number) {
            this._machine[2] = machine & 0xff
            this._machine[1] = (machine >> 8) & 0xff
            this._machine[0] = (machine >> 16) & 0xff
        }

        set counter(count: number) {
            this._counter[2] = count & 0xff
            this._counter[1] = (count >> 8) & 0xff
            this._counter[0] = (count >> 16) & 0xff
        }

        toHexString() {
            return Array
                .prototype
                .map
                .call(
                    new Uint8Array(this.buffer),
                    x => ('00' + x.toString(16)).slice(-2)).join('')
        }

        fromHexString(str: string) {
            const buf = new Uint8Array(this.buffer)
            str.match(/.{1,2}/g).forEach((v, idx) => {
                buf[idx] = parseInt(v, 16)
            })
        }

        get timestamp() {
            return this._timestamp[3] + this._timestamp[2] << 8 + this._timestamp[1] << 16 + this._timestamp[0] << 24
        }

        get machineIdentifier() {
            return this._machine[2] + this._machine[1] << 16 + this._machine[0] << 8
        }

        get processIdentifier() {
            return this._process[1] + this._process[0] << 8

        }

        get counter() {
            return this._counter[2] << 16 + this._counter[1] << 8 + this._counter[0]
        }

        new() {
            this.processIdentifier = state._processIdentifier
            this.machineIdentifier = state._machineIdentifier
            this.counter = state.inc
            this.timestamp = state.timestamp
        }
    }

    class Cacher {
        _inc: number=-1
        _machineIdentifier: number=-1
        _processIdentifier: number=-1
        private _loaded: boolean = false
        private _useLocal: boolean = false
        private _useSession: boolean = false
        private _useCookie: boolean = false

        load() {
            this._loaded = false
            this.fromLocalStore()
            if (!this._loaded) {
                this.fromSessionStore()
            } else {
                this.saveSessionStore()
                return this
            }
            if (!this._loaded) {
                this.fromCookie()
            } else {
                this.saveCookie()
                return this
            }
            if (!this._loaded) {
                this._machineIdentifier = Math.floor(Math.random() * (16777216))
                this._processIdentifier = Math.floor(Math.random() * (65536))
                this._inc = 0
            }
            this._loaded = true
            return this

        }

        save() {
            if (this._useLocal) {
                this.saveLocalStore()
            } else if (this._useSession) {
                this.saveSessionStore()
            } else if (this._useCookie) {
                this.saveCookie()
            } else {
                console.error("could not save ")
            }
        }

        saveSessionStore() {
            sessionStorage.setItem("bson", JSON.stringify(
                {
                    _inc: this._inc,
                    _machineIdentifier: this._machineIdentifier,
                    _processIdentifier: this._processIdentifier,
                }
            ))
        }

        saveLocalStore() {
            localStorage.setItem("bson", JSON.stringify(
                {
                    _inc: this._inc,
                    _machineIdentifier: this._machineIdentifier,
                    _processIdentifier: this._processIdentifier,
                }
            ))
        }

        saveCookie() {
            if (document.hasOwnProperty("cookie")) {
                const tab = document.cookie.split(";").map(e => e.split("="))
                const bson = tab.filter(e => e[0] == "bson")
                if (bson.length == 1) {
                    bson[0][1] = JSON.stringify({
                        _inc: this._inc,
                        _machineIdentifier: this._machineIdentifier,
                        _processIdentifier: this._processIdentifier,
                    })
                } else {
                    tab.push(["bson", JSON.stringify(
                        {
                            _inc: this._inc,
                            _machineIdentifier: this._machineIdentifier,
                            _processIdentifier: this._processIdentifier,
                        }
                    )])
                }
                document.cookie = tab.map(e => e.join("=")).join(";")

            }
        }

        fromSessionStore() {
            let cache = sessionStorage.getItem("bson")
            if (cache == null) return
            const load = JSON.parse(cache)
            this._machineIdentifier = load._machineIdentifier
            this._processIdentifier = load._processIdentifier
            this._inc = load._inc
            this._loaded = true
        }

        fromLocalStore() {
            let cache = localStorage.getItem("bson")
            if (cache == null) return
            const load = JSON.parse(cache)
            this._machineIdentifier = load._machineIdentifier
            this._processIdentifier = load._processIdentifier
            this._inc = load._inc
            this._loaded = true
            this._useLocal = true
        }

        fromCookie() {
            if (document.hasOwnProperty("cookie")) {
                this._useCookie = true
                const tab = document.cookie.split(";").map(e => e.split("="))
                const bson = tab.filter(e => e[0] == "bson")
                if (bson.length == 1) {
                    const load = JSON.parse(bson[0][1])
                    this._machineIdentifier = load._machineIdentifier
                    this._processIdentifier = load._processIdentifier
                    this._inc = load._inc
                    this._loaded = true
                    return
                }
            }
        }

        get inc() {
            this._inc = (this._inc + 1) % 0xffffff
            this.save()
            return this._inc
        }

        get timestamp() {
            return new Date().getTime() * 1000
        }

    }

    const state = new Cacher().load()

   export class BsonId {
        get timestamp(): number {
            return this.buf.timestamp
        }

        get machineIdentifier(): number {
            return this.buf.machineIdentifier
        }

        get processIdentifier(): number {
            return this.buf.processIdentifier
        }

        get counter(): number {
            return this.buf.counter
        }

        get date() {
            return new Date(this.timestamp * 1000)
        }

        private buf: Buffer

        constructor(hex: string | null=null) {
            this.buf = new Buffer()
            if (hex && hex.length == 24 && hex.match(BsonIdRegx)) {
                this.buf.fromHexString(hex)
            } else {
                this.buf.new()
            }

        }
        toString(){
            return this.buf.toHexString()
        }
    }

    export class BsonShortId {
        get timestamp(): number {
            return this.id.timestamp
        }

        get machineIdentifier(): number {
            return this.id.machineIdentifier
        }

        get processIdentifier(): number {
            return this.id.processIdentifier
        }

        get counter(): number {
            return this.id.counter
        }

        get date() {
            return new Date(this.timestamp * 1000)
        }

        private id: BsonId

        constructor(hex: string | null=null) {
            if (hex && hex.length == 16 && hex.match(BsonShortIdRegx)) {
                this.id = new BsonId(decompress(hex))
            } else {
                this.id = new BsonId()
            }

        }
        toString(){
            return this.id.toString()
        }
    }



    export function fromHex(hex: string | null) {
        return new BsonId(hex)
    }
}
const BsonHelper = {
    from(hex: string | null) {
        return Bson.fromHex(hex)
    },
    get() {
        return Bson.fromHex(null)
    }
}
export default BsonHelper
export const BsonId=Bson.BsonId
export const BsonShortId=Bson.BsonShortId
